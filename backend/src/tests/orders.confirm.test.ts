import Fastify from "fastify";
import ordersRoutes from "../routes/orders";

jest.mock("../supabase", () => {
  const supabaseAdmin: any = {
    auth: {
      getUser: jest.fn(),
    },
    rpc: jest.fn(),
    from: jest.fn(),
  };

  // default from for admins lookup
  supabaseAdmin.from.mockImplementation((table: string) => {
    if (table === "admins") {
      return {
        select: () => ({
          eq: () => ({
            limit: () => ({
              maybeSingle: async () => ({
                data: { id: "admin-uuid" },
                error: null,
              }),
            }),
          }),
        }),
      };
    }
    // fallback
    return {
      select: () => ({
        maybeSingle: async () => ({ data: null, error: null }),
      }),
    };
  });

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
    jest.clearAllMocks();
  });

  it("RPC returns row -> 200 with data", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // Mock rpc to return updated order row
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [
        {
          id: "order-1",
          client_id: "client-1",
          status: "confirmed",
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        },
      ],
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

    // rpc returns empty array -> no row changed (already confirmed)
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [],
      error: null,
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/order-2/confirm",
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

    // simulate RPC error
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: null,
      error: { message: "something bad" },
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/order-3/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(500);
  });
});
