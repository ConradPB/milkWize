import { FastifyInstance } from "fastify";
import crypto from "crypto";

export default async function webhookRoutes(server: FastifyInstance) {
  server.post("/api/webhook/payment", async (request, reply) => {
    try {
      // Received header
      const receivedSigHeader =
        (request.headers["x-webhook-signature"] as string) || "";

      // Server raw body string (this is what JSON.stringify(request.body) would be)
      // Keep exact same canonicalization used in verification
      const raw = JSON.stringify(request.body || {});

      // Compute HMAC on server using WEBHOOK_SECRET
      const secret = process.env.WEBHOOK_SECRET || "";
      const computedHmac = secret
        ? crypto.createHmac("sha256", secret).update(raw).digest("hex")
        : "";

      // Log debug info (temporary)
      server.log.info({
        debug: "webhook-debug",
        rawBody: raw,
        receivedSigHeader,
        computedHmac,
        computedPrefixed: `sha256=${computedHmac}`,
      });

      // Accept either raw hex or sha256=<hex>
      const matches =
        receivedSigHeader === computedHmac ||
        receivedSigHeader === `sha256=${computedHmac}`;

      if (!matches) {
        return reply.status(403).send({ error: "Invalid signature" });
      }

      // TODO: process payment webhook payload here...
      server.log.info({ msg: "valid webhook", body: request.body });
      return reply.send({ ok: true });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: "server error" });
    }
  });
}
