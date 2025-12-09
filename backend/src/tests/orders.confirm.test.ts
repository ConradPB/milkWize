/**
 * Updated orders.confirm.test.ts
 * Uses valid UUIDs for order id and caller uid. Mocks rpc & from behaviour.
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
    const ORDER_ID = "3a24e641-1921-4891-92c0-0d5025e87354";
    const CALLER_UID = "cd5526ab-ec7a-4ade-ba7f-272eda966219";

    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: CALLER_UID } },
      error: null,
    });

    // RPC returns the updated order row (array)
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [
        {
          id: ORDER_ID,
          status: "confirmed",
          client_id: "863c02b4-490e-4615-926f-34db2fc90d1a",
        },
      ],
      error: null,
    });

    const res = await app.inject({
      method: "PATCH",
      url: `/api/orders/${ORDER_ID}/confirm`,
      headers: { Authorization: "Bearer client-token" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(body.data.status).toBe("confirmed");
  });

  it("RPC empty -> already confirmed (idempotent): return message + order", async () => {
    const ORDER_ID = "22222222-2222-2222-2222-222222222222";
    const CALLER_UID = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa";

    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: CALLER_UID } },
      error: null,
    });

    // RPC returns empty -> route should fetch order row and return idempotent message
    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: [],
      error: null,
    });

    // mock .from('orders').select(...).eq(...).limit(1).maybeSingle()
    (supabaseAdmin.from as jest.Mock).mockImplementation((table: string) => {
      if (table === "orders") {
        return {
          select: () => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: {
                    id: ORDER_ID,
                    status: "confirmed",
                    client_id: "863c02b4-490e-4615-926f-34db2fc90d1a",
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
      url: `/api/orders/${ORDER_ID}/confirm`,
      headers: { Authorization: "Bearer client-token" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    // flexible checks: route may return message + order or an order field
    expect(body.message || body.order || body.data).toBeDefined();
  });

  it("RPC error -> 500", async () => {
    const ORDER_ID = "33333333-3333-3333-3333-333333333333";
    const CALLER_UID = "bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb";

    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: CALLER_UID } },
      error: null,
    });

    (supabaseAdmin.rpc as jest.Mock).mockResolvedValue({
      data: null,
      error: { message: "rpc failed" },
    });

    const res = await app.inject({
      method: "PATCH",
      url: `/api/orders/${ORDER_ID}/confirm`,
      headers: { Authorization: "Bearer client-token" },
    });

    expect(res.statusCode).toBe(500);
  });
});
