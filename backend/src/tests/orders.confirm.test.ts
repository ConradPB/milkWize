// src/tests/orders.confirm.test.ts
import Fastify from "fastify";
import ordersRoutes from "../routes/orders";

const supabaseAdminMock: any = {
  auth: { getUser: jest.fn() },
  rpc: jest.fn(),
  from: jest.fn(),
};

jest.mock("../supabase", () => ({ supabaseAdmin: supabaseAdminMock }));

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
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // rpc returns the updated order row as array
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
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
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // rpc returns empty array (already confirmed)
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [],
      error: null,
    });

    // The route expects us to interpret empty -> already confirmed and fetch order.
    // Mock a subsequent .from('orders').select(...) to return the order row.
    (supabaseAdmin.from as unknown as jest.Mock).mockImplementation(
      (table: string) => {
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
      }
    );

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
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
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
