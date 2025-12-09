/**
 *
 * Tests the /api/milking_events route.
 *
 * Important: this file defines an inline jest.mock factory that returns
 * a mock supabaseAdmin object. That avoids "cannot access ... before initialization"
 * issues with hoisted jest.mock calls.
 */

jest.mock("../supabase", () => {
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
    // register formbody plugin used by the route
    await app.register(require("@fastify/formbody"));
    await app.register(milkingRoutes);
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    jest.clearAllMocks();

    // default table-aware mock implementation
    (supabaseAdmin.from as jest.Mock).mockImplementation((table: string) => {
      // helpers to return common shapes
      const maybeSingleReturn = (data: any) => ({
        maybeSingle: async () => ({ data, error: null }),
      });

      const selectEqMaybeSingle = (data: any) => ({
        select: (_sel?: string) => ({
          eq: (_col: string, _val: any) => ({
            limit: (_n?: number) => maybeSingleReturn(data),
            maybeSingle: async () => ({ data, error: null }),
          }),
          maybeSingle: async () => ({ data, error: null }),
        }),
      });

      // Table-specific shapes
      if (table === "admins") {
        return {
          select: (_sel?: string) => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: { id: "15c598fd-b18b-4e00-a480-6753d7f0f5e8" },
                  error: null,
                }),
              }),
              maybeSingle: async () => ({
                data: { id: "15c598fd-b18b-4e00-a480-6753d7f0f5e8" },
                error: null,
              }),
            }),
          }),
        };
      }

      if (table === "cows") {
        return {
          select: (_sel?: string) => ({
            eq: (_col: string, _val: any) => ({
              limit: (_n?: number) => ({
                maybeSingle: async () => ({
                  data: {
                    id: "73052e07-e1ac-48fc-9710-57c4deb52712",
                    tag: "COW-001",
                  },
                  error: null,
                }),
              }),
              maybeSingle: async () => ({
                data: {
                  id: "73052e07-e1ac-48fc-9710-57c4deb52712",
                  tag: "COW-001",
                },
                error: null,
              }),
            }),
          }),
        };
      }

      if (table === "milking_events") {
        return {
          insert: (payload: any[]) => ({
            select: async () => ({
              data: payload.map((p, i) => ({
                id: `11111111-1111-1111-1111-00000000000${i}`,
                ...p,
              })),
              error: null,
            }),
          }),
          // in case route queries milking_events with select(...) elsewhere
          select: (_sel?: string) => ({
            eq: (_col: string, _val: any) => ({
              maybeSingle: async () => ({ data: null, error: null }),
            }),
            maybeSingle: async () => ({ data: null, error: null }),
          }),
        };
      }

      // default safe object for other tables
      return {
        select: (_sel?: string) => ({
          eq: (_col: string, _val: any) => ({
            limit: (_n?: number) => ({
              maybeSingle: async () => ({ data: null, error: null }),
            }),
            maybeSingle: async () => ({ data: null, error: null }),
          }),
          maybeSingle: async () => ({ data: null, error: null }),
        }),
        insert: (payload: any[]) => ({
          select: async () => ({ data: payload, error: null }),
        }),
      };
    });
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
    // make auth.getUser return a valid admin uid
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "9ffd41d9-5d73-4fc5-b160-b523a1215677" } },
      error: null,
    });

    const payload = {
      cow_id: "73052e07-e1ac-48fc-9710-57c4deb52712",
      milk_liters: 9.25,
      milking_time: "2025-11-15T07:00:00Z",
    };

    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      headers: { Authorization: "Bearer some-valid-token" },
      payload,
    });

    // debug output if something goes wrong
    if (res.statusCode !== 201) {
      // eslint-disable-next-line no-console
      console.log("DEBUG RESPONSE PAYLOAD:", res.payload);
    }

    expect(res.statusCode).toBe(201);

    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].id).toBeDefined();

    // ensure milking_events insert was called (by checking mock calls)
    const calledTables = (supabaseAdmin.from as jest.Mock).mock.calls.map(
      (c: any) => c[0]
    );
    expect(calledTables).toContain("milking_events");
  });
});
