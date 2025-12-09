/**
 *
 * Tests the /api/milking_events route.
 *
 * Important: this file defines an inline jest.mock factory that returns
 * a mock supabaseAdmin object. That avoids "cannot access ... before initialization"
 * issues with hoisted jest.mock calls.
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
import milkingRoutes from "../routes/milking";
import Fastify from "fastify";

describe("milking route", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify();
    // register the real plugin name you use in the app
    await app.register(require("@fastify/formbody"));
    await app.register(milkingRoutes);
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
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
    // Arrange: mock supabaseAdmin.auth.getUser to return a user id
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "test-user-uid" } },
      error: null,
    });

    // Mock admins lookup to map auth_uid -> admins.id
    (supabaseAdmin.from as jest.Mock).mockImplementation((table: string) => {
      if (table === "admins") {
        return {
          select: () => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: { id: "admin-uuid-1" },
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
              data: payload.map((p) => ({ id: "milking-1", ...p })),
              error: null,
            }),
          }),
        };
      }

      // fallback generic
      return {
        select: () => ({ then: async () => ({ data: [], error: null }) }),
      };
    });

    // Act: call endpoint with JWT header and valid payload
    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      headers: {
        Authorization: "Bearer some-valid-token",
      },
      payload: {
        cow_id: "73052e07-e1ac-48fc-9710-57c4deb52712",
        milk_liters: 9.25,
        milking_time: "2025-11-15T07:00:00Z",
      },
    });

    // Assert
    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].id).toBe("milking-1");

    // Ensure we used supabase insert
    expect(supabaseAdmin.from).toHaveBeenCalledWith("milking_events");
  });
});
