import { FastifyInstance } from "fastify";
import crypto from "crypto";

export default async function webhookRoutes(server: FastifyInstance) {
  server.post("/api/webhook/payment", async (request, reply) => {
    try {
      const received = (request.headers["x-webhook-signature"] as string) || "";
      const raw = JSON.stringify(request.body || {});
      const secret = process.env.WEBHOOK_SECRET || "";

      if (!secret) {
        server.log.error("Missing WEBHOOK_SECRET in environment");
        return reply.status(500).send({ error: "server misconfiguration" });
      }

      const computed = crypto
        .createHmac("sha256", secret)
        .update(raw)
        .digest("hex");

      // Accept either raw hex or prefixed form
      const ok = received === computed || received === `sha256=${computed}`;
      if (!ok) return reply.status(403).send({ error: "Invalid signature" });

      // TODO: actual webhook processing here
      return reply.send({ ok: true });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: "server error" });
    }
  });
}
