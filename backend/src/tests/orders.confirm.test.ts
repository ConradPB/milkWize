// src/tests/orders.confirm.test.ts
import Fastify from "fastify";
import ordersRoutes from "../routes/orders";

jest.mock("../supabase", () => {
  const supabaseAdmin = {
    auth: { getUser: jest.fn() },
    rpc: jest.fn(),
    from: jest.fn(),
  };
  return { supabaseAdmin };
});

import { supabaseAdmin } from "../supabase";

describe("PATCH /api/orders/:id/confirm", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    await app.register(require("@fastify/formbody"));
    await app.register(ordersRoutes);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    jest.resetAllMocks();
  });

  it("RPC returns row -> 200 with data", async () => {
    (supabaseAdmin as any).auth.getUser.mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });
    (supabaseAdmin as any).rpc.mockResolvedValue({
      data: [{ id: "order-1", status: "confirmed", client_id: "client-1" }],
      error: null,
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/order-1/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(body.data.status).toBe("confirmed");
  });

  it("RPC empty -> already confirmed -> idempotent 200", async () => {
    (supabaseAdmin as any).auth.getUser.mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });
    (supabaseAdmin as any).rpc.mockResolvedValue({ data: [], error: null });

    (supabaseAdmin as any).from.mockImplementation((table: string) => {
      if (table === "orders") {
        return {
          select: () => ({
            eq: () => ({
              limit: () => ({
                maybeSingle: async () => ({
                  data: { id: "order-1", status: "confirmed" },
                  error: null,
                }),
              }),
            }),
          }),
        };
      }
      return {
        select: () => ({
          maybeSingle: async () => ({ data: null, error: null }),
        }),
      };
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/order-1/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.message).toMatch(/already confirmed/i);
    expect(body.order).toBeDefined();
  });

  it("RPC error -> 500", async () => {
    (supabaseAdmin as any).auth.getUser.mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });
    (supabaseAdmin as any).rpc.mockResolvedValue({
      data: null,
      error: { message: "rpc failed" },
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/order-1/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(500);
  });
});
