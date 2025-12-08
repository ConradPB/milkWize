import Fastify from "fastify";
import milkingRoutes from "../routes/milking";

jest.mock("../supabase", () => {
  return {
    supabaseAdmin: {
      auth: {
        getUser: jest.fn(),
      },
      from: jest.fn(),
    },
  };
});

import { supabaseAdmin } from "../supabase";

describe("milking route", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    // use the scoped package plugin
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
    // mock auth.getUser to return a user (the server will then map to admin)
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // Mock admin lookup: .from('admins').select(...).eq(...).limit(1).maybeSingle()
    (supabaseAdmin.from as jest.Mock).mockReturnValueOnce({
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
    });

    // Mock insert to milking_events table
    (supabaseAdmin.from as jest.Mock).mockReturnValueOnce({
      insert: () => ({
        select: async () => ({
          data: [{ id: "milking-1", milk_liters: 5 }],
          error: null,
        }),
      }),
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
