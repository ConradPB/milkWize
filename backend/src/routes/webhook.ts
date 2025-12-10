import { FastifyInstance } from "fastify";
import crypto from "crypto";

export default async function webhookRoutes(server: FastifyInstance) {
  // NOTE: for perfect safety, register fastify-raw-body in your main server:
  // await server.register(require('fastify-raw-body'), {
  //   field: 'rawBody', // request.rawBody will be set
  //   global: false,    // set true to enable for all routes
  //   encoding: 'utf8',
  //   runFirst: true
  // });

  server.post("/api/webhook/payment", async (request, reply) => {
    try {
      const secret = process.env.WEBHOOK_SECRET || "";
      if (!secret) {
        server.log.error("Missing WEBHOOK_SECRET in environment");
        return reply.status(500).send({ error: "server misconfiguration" });
      }

      // header may be 'sha256=<hex>' or raw '<hex>'
      const rawHeader = (request.headers["x-webhook-signature"] ||
        "") as string;
      if (!rawHeader)
        return reply.status(403).send({ error: "Missing signature" });

      // Extract hex part if prefixed with "sha256="
      const receivedHex = rawHeader.startsWith("sha256=")
        ? rawHeader.slice(7)
        : rawHeader;

      // Prefer raw bytes if available (register fastify-raw-body). Otherwise fallback.
      let rawBodyString: string;
      // @ts-ignore - rawBody may be set by fastify-raw-body plugin as request.rawBody
      if (
        (request as any).rawBody &&
        typeof (request as any).rawBody === "string"
      ) {
        rawBodyString = (request as any).rawBody;
      } else if (typeof request.body === "string") {
        rawBodyString = request.body;
      } else {
        // fallback: canonicalize JSON (works for tests but may mismatch provider)
        rawBodyString = JSON.stringify(request.body || {});
      }

      const computed = crypto
        .createHmac("sha256", secret)
        .update(rawBodyString, "utf8")
        .digest("hex");

      // Use timingSafeEqual on buffers — protects vs timing attacks
      const receivedBuf = Buffer.from(receivedHex, "hex");
      const computedBuf = Buffer.from(computed, "hex");

      let ok = false;
      if (receivedBuf.length === computedBuf.length) {
        ok = crypto.timingSafeEqual(receivedBuf, computedBuf);
      }

      if (!ok) {
        server.log.warn({
          msg: "webhook signature mismatch",
          headerPresent: !!rawHeader,
          computedLen: computedBuf.length,
          receivedLen: receivedBuf.length,
        });
        return reply.status(403).send({ error: "Invalid signature" });
      }

      // TODO: process webhhook payload (idempotency / enqueue job / verify txn)
      // Example: server.log.info({ msg: 'webhook received', body: request.body });

      return reply.status(200).send({ ok: true });
    } catch (err: any) {
      server.log.error({
        msg: "webhook handler error",
        err: err?.message || err,
      });
      return reply.status(500).send({ error: "server error" });
    }
  });
}
