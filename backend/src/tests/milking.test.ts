import Fastify from "fastify";
import milkingRoutes from "../routes/milking";

jest.mock("../supabase", () => {
  // Replace supabaseAdmin.from with a dispatcher that returns
  // objects shaped like the real supabase-js chaining.
  const supabaseAdmin: any = {
    auth: {
      getUser: jest.fn(),
    },
    from: jest.fn(),
  };

  // Implement a simple dispatcher for .from(table)
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

    if (table === "milking_events") {
      return {
        insert: (payload: any[]) => ({
          select: async () => ({
            data: [{ id: "milking-1", ...payload[0] }],
            error: null,
          }),
        }),
      };
    }

    // Default fallback for other tables (not used in this test)
    return {
      select: () => ({
        maybeSingle: async () => ({ data: null, error: null }),
      }),
    };
  });

  return { supabaseAdmin };
});

import { supabaseAdmin } from "../supabase";

describe("milking route", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    await app.register(require("@fastify/formbody"));
    await app.register(milkingRoutes);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    jest.clearAllMocks();
  });

  it("returns 401 without JWT", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      payload: {},
    });
    expect(res.statusCode).toBe(401);
  });

  it("inserts milking_event when JWT valid", async () => {
    // Mock auth.getUser to return a user id so admin mapping succeeds
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    const payload = {
      cow_id: "73052e07-e1ac-48fc-9710-57c4deb52712",
      milk_liters: 5,
      milking_time: "2025-11-15T07:00:00Z",
    };

    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      headers: { Authorization: "Bearer dummy" },
      payload,
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].milk_liters).toBe(5);
  });
});
