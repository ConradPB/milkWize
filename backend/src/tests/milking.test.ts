/**
 *
 * Tests the /api/milking_events route.
 *
 * Important: this file defines an inline jest.mock factory that returns
 * a mock supabaseAdmin object. That avoids "cannot access ... before initialization"
 * issues with hoisted jest.mock calls.
 */

jest.mock("../supabase", () => {
  const supabaseAdminMock = {
    auth: { getUser: jest.fn() },
    from: jest.fn(),
    rpc: jest.fn(),
  };
  return { supabaseAdmin: supabaseAdminMock };
});

import { supabaseAdmin } from "../supabase";
import milkingRoutes from "../routes/milking";
import Fastify from "fastify";

describe("milking route", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify();
    // use the installed fastify plugin name exactly
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
    // valid UUIDs for test data
    const TEST_ADMIN_UID = "9ffd41d9-5d73-4fc5-b160-b523a1215677"; // auth user sub
    const ADMIN_ROW_ID = "15c598fd-b18b-4e00-a480-6753d7f0f5e8"; // admins.id (uuid)
    const COW_ID = "73052e07-e1ac-48fc-9710-57c4deb52712"; // cow uuid

    // Mock auth.getUser to return a user id
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: TEST_ADMIN_UID } },
      error: null,
    });

    // Provide a flexible .from mock that returns chainable objects depending on table
    (supabaseAdmin.from as jest.Mock).mockImplementation((table: string) => {
      if (table === "admins") {
        return {
          select: () => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: { id: ADMIN_ROW_ID },
                  error: null,
                }),
              }),
            }),
          }),
        };
      }

      if (table === "cows") {
        // If your route verifies the cow exists first
        return {
          select: () => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: { id: COW_ID, tag: "COW-001" },
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
            // .select() should return the inserted rows
            select: async () => ({
              data: payload.map((p) => ({
                id: "11111111-1111-1111-1111-111111111111",
                ...p,
              })),
              error: null,
            }),
          }),
        };
      }

      // fallback safe object
      return {
        select: () => ({ then: async () => ({ data: [], error: null }) }),
      };
    });

    // Send request with a valid-looking JWT and payload that matches route expectations
    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      headers: {
        Authorization: "Bearer some-valid-token",
      },
      payload: {
        cow_id: COW_ID,
        milk_liters: 9.25,
        milking_time: "2025-11-15T07:00:00Z",
      },
    });

    // Assert
    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].id).toBeDefined();

    // verify we touched the milking_events table
    expect(supabaseAdmin.from).toHaveBeenCalledWith("milking_events");
  });
});
