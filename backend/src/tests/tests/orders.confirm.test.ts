import Fastify from "fastify";
import ordersRoutes from "../../routes/orders";

jest.mock("../src/supabase", () => {
  const supabaseAdminMock = {
    auth: { getUser: jest.fn() },
    rpc: jest.fn(),
    from: jest.fn(),
  };
  return { supabaseAdmin: supabaseAdminMock };
});

import { supabaseAdmin } from "../../";

describe("PATCH /api/orders/:id/confirm", () => {
  let server: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    server = Fastify({ logger: false });
    await server.register(ordersRoutes);
    await server.ready();
    jest.clearAllMocks();
  });

  afterEach(async () => {
    await server.close();
  });

  test("RPC returns row -> 200 with data", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [{ id: "order-1", status: "confirmed" }],
      error: null,
    });

    const res = await server.inject({
      method: "PATCH",
      url: "/api/orders/order-1/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(body.data.status).toBe("confirmed");
  });

  test("RPC empty -> already confirmed -> 200 idempotent message", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [],
      error: null,
    });

    // Mock .from(...).select(...).eq(...).limit(...).maybeSingle() chain to return confirmed order
    (supabaseAdmin.from as jest.Mock).mockReturnValue({
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
    });

    const res = await server.inject({
      method: "PATCH",
      url: "/api/orders/order-1/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.message).toMatch(/already confirmed/i);
    expect(body.order).toBeDefined();
  });

  test("RPC error -> 500", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: null,
      error: { message: "rpc fail" },
    });

    const res = await server.inject({
      method: "PATCH",
      url: "/api/orders/order-1/confirm",
      headers: { Authorization: "Bearer dummy" },
    });

    expect(res.statusCode).toBe(500);
  });
});
