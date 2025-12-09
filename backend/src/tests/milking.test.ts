/**
 *
 * Tests the /api/milking_events route.
 *
 * Important: this file defines an inline jest.mock factory that returns
 * a mock supabaseAdmin object. That avoids "cannot access ... before initialization"
 * issues with hoisted jest.mock calls.
 */

jest.mock("../supabase", () => {
  // We'll export supabaseAdmin which we'll stub per-test
  const supabaseAdminMock: any = {
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
    const TEST_ADMIN_UID = "9ffd41d9-5d73-4fc5-b160-b523a1215677";
    const ADMIN_ROW_ID = "15c598fd-b18b-4e00-a480-6753d7f0f5e8";
    const COW_ID = "73052e07-e1ac-48fc-9710-57c4deb52712";

    // 1) auth.getUser should return the caller uid
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: TEST_ADMIN_UID } },
      error: null,
    });

    // Prepare sequential .from mocks (exact call order used by your route):
    //  - first call: .from('admins').select()...maybeSingle()  -> return admin row
    //  - second call (optional if route checks cow): .from('cows')...maybeSingle() -> return cow
    //  - third call: .from('milking_events').insert(...).select() -> return inserted row(s)

    // Mock for admins lookup
    (supabaseAdmin.from as jest.Mock).mockImplementationOnce(
      (table: string) => {
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
        // fallback (shouldn't be used for this call)
        return {
          select: () => ({
            maybeSingle: async () => ({ data: null, error: null }),
          }),
        };
      }
    );

    // Mock for cows lookup (if your route checks cow exists first).
    // If your route does NOT call cows lookup - this still won't break because we use mockImplementationOnce.
    (supabaseAdmin.from as jest.Mock).mockImplementationOnce(
      (table: string) => {
        if (table === "cows") {
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
        return {
          select: () => ({
            maybeSingle: async () => ({ data: null, error: null }),
          }),
        };
      }
    );

    // Mock for milking_events insert -> select()
    (supabaseAdmin.from as jest.Mock).mockImplementationOnce(
      (table: string) => {
        if (table === "milking_events") {
          return {
            insert: (payload: any[]) => ({
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
        // default safe object
        return {
          insert: () => ({ select: async () => ({ data: [], error: null }) }),
        };
      }
    );

    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      headers: { Authorization: "Bearer some-valid-token" },
      payload: {
        cow_id: COW_ID,
        milk_liters: 9.25,
        milking_time: "2025-11-15T07:00:00Z",
      },
    });

    // If the test fails, print payload to help debugging
    if (res.statusCode !== 201) {
      // eslint-disable-next-line no-console
      console.log("DEBUG RESPONSE PAYLOAD:", res.payload);
    }

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].id).toBeDefined();

    // verify that we called milking_events at least once
    expect(
      (supabaseAdmin.from as jest.Mock).mock.calls.some(
        (c: any) => c[0] === "milking_events"
      )
    ).toBe(true);
  });
});
