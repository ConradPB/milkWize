/**

 *
 * Tests PATCH /api/orders/:id/confirm
 *
 * Scenarios:
 *  - RPC returns a row -> route returns 200 + data
 *  - RPC returns [] (already confirmed) -> route returns 200 idempotent message + order row
 *  - RPC returns error -> route returns 500
 */

jest.mock("../supabase", () => {
  const supabaseAdmin = {
    auth: { getUser: jest.fn() },
    from: jest.fn(),
    rpc: jest.fn(),
  };
  return { supabaseAdmin };
});

import { supabaseAdmin } from "../supabase";
import ordersRoutes from "../routes/orders";
import Fastify from "fastify";

describe("PATCH /api/orders/:id/confirm", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify();
    await app.register(require("@fastify/formbody"));
    await app.register(ordersRoutes);
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("RPC returns row -> 200 with data", async () => {
    // caller JWT resolves to user id (client)
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "client-uid-1" } },
      error: null,
    });

    // RPC returns the updated order row
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [{ id: "ord-1", status: "confirmed", client_id: "c1" }],
      error: null,
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/ord-1/confirm",
      headers: {
        Authorization: "Bearer client-token",
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(body.data.status).toBe("confirmed");
  });

  it("RPC empty -> already confirmed (idempotent): return message + order", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "client-uid-1" } },
      error: null,
    });

    // RPC returns empty (already confirmed)
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [],
      error: null,
    });

    // Then the route should fetch the order to show it is already confirmed
    (supabaseAdmin.from as jest.Mock).mockImplementation((table: string) => {
      if (table === "orders") {
        return {
          select: () => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: {
                    id: "ord-2",
                    status: "confirmed",
                    client_id: "c2",
                    created_at: new Date().toISOString(),
                  },
                  error: null,
                }),
              }),
            }),
          }),
        };
      }
      return {
        select: () => ({ then: async () => ({ data: [], error: null }) }),
      };
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/ord-2/confirm",
      headers: {
        Authorization: "Bearer client-token",
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.message || body.msg || body.order).toBeDefined();
    // If your route returns order field use that; adapt if your route shape differs
    expect(body.order || body.data || body.msg).toBeDefined();
  });

  it("RPC error -> 500", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "client-uid-1" } },
      error: null,
    });

    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: null,
      error: { message: "rpc failed" },
    });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/orders/ord-3/confirm",
      headers: {
        Authorization: "Bearer client-token",
      },
    });

    expect(res.statusCode).toBe(500);
  });
});
