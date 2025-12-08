import Fastify from "fastify";
import webhookRoutes from "../src/routes/webhook";
import crypto from "crypto";

describe("POST /api/webhook/payment", () => {
  let server: ReturnType<typeof Fastify>;
  beforeEach(async () => {
    server = Fastify({ logger: false });
    await server.register(webhookRoutes);
    await server.ready();
  });
  afterEach(async () => await server.close());

  test("Valid signature -> 200", async () => {
    const secret = "test-secret";
    process.env.WEBHOOK_SECRET = secret; // ensure route reads this env var
    const payload = JSON.stringify({ txn_ref: "abc-1", amount: 1000 });
    const sig = crypto
      .createHmac("sha256", secret)
      .update(payload)
      .digest("hex");

    const res = await server.inject({
      method: "POST",
      url: "/api/webhook/payment",
      payload,
      headers: {
        "x-webhook-signature": sig,
        "content-type": "application/json",
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.ok).toBe(true);
  });

  test("Invalid signature -> 403", async () => {
    process.env.WEBHOOK_SECRET = "test-secret";
    const payload = JSON.stringify({ txn_ref: "abc-1", amount: 1000 });
    const res = await server.inject({
      method: "POST",
      url: "/api/webhook/payment",
      payload,
      headers: {
        "x-webhook-signature": "bad",
        "content-type": "application/json",
      },
    });
    expect(res.statusCode).toBe(403);
  });
});
