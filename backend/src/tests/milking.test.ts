import Fastify from "fastify";
import milkingRoutes from "../routes/milking";

// mocked supabase
const supabaseAdminMock = {
  auth: {
    getUser: jest.fn(),
  },
  from: jest.fn(),
};

jest.mock("../supabase", () => ({ supabaseAdmin: supabaseAdminMock }));

import { supabaseAdmin } from "../supabase";

describe("milking route", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    // register same plugin as production code
    await app.register(require("@fastify/formbody"));
    await app.register(milkingRoutes);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    jest.resetAllMocks();
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
    // 1) auth.getUser returns valid user id
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // 2) from('admins').select(...).maybeSingle() -> admin row
    (supabaseAdmin.from as unknown as jest.Mock).mockImplementation(
      (table: string) => {
        if (table === "admins") {
          return {
            select: () => ({
              eq: () => ({
                limit: () => ({
                  maybeSingle: async () => ({
                    data: { id: "admin-row-uuid" },
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
                data: payload.map((p) => ({ ...p, id: "inserted-uuid" })),
                error: null,
              }),
            }),
          };
        }

        // default safe object
        return {
          select: () => ({
            maybeSingle: async () => ({ data: null, error: null }),
          }),
        };
      }
    );

    const payload = {
      cow_id: "73052e07-e1ac-48fc-9710-57c4deb52712",
      milk_liters: 3.5,
      milking_time: "2025-11-15T07:00:00Z",
    };

    const res = await app.inject({
      method: "POST",
      url: "/api/milking_events",
      headers: { Authorization: "Bearer dummy-token" },
      payload,
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].milk_liters).toBe(payload.milk_liters);
  });
});
