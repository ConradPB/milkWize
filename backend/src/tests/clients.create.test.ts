import Fastify from "fastify";
import clientsRoutes from "../routes/clients";

// Provide a mocked supabaseAdmin factory. from is a jest.fn so we it can be over ridden per-test.
jest.mock("../supabase", () => {
  const supabaseAdmin: any = {
    auth: {
      getUser: jest.fn(),
    },
    from: jest.fn(),
  };

  // Default behavior for .from('admins') used by the route to map JWT -> admin id
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

    // Default fallback for other tables
    return {
      select: () => ({
        maybeSingle: async () => ({ data: null, error: null }),
      }),
    };
  });

  return { supabaseAdmin };
});

import { supabaseAdmin } from "../supabase";

describe("POST /api/clients", () => {
  let app: any;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    await app.register(require("@fastify/formbody"));
    await app.register(clientsRoutes);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    jest.clearAllMocks();
  });

  it("Insert new client -> 201", async () => {
    // mock admin verification
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // Override .from behavior for this test: clients.insert -> return created client
    (supabaseAdmin.from as unknown as jest.Mock).mockImplementationOnce(
      (table: string) => {
        if (table === "clients") {
          return {
            insert: (payload: any[]) => ({
              select: async () => ({
                data: [{ id: "new-client-id", ...payload[0] }],
                error: null,
              }),
            }),
          };
        }
        // fallback uses admins default (set earlier)
        return {
          select: () => ({
            maybeSingle: async () => ({
              data: { id: "admin-uuid" },
              error: null,
            }),
          }),
        };
      }
    );

    const res = await app.inject({
      method: "POST",
      url: "/api/clients",
      headers: { Authorization: "Bearer dummy" },
      payload: {
        name: "Test Person",
        phone: "+256700000111",
        address: "Kampala",
        preferred_window: "morning",
      },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].phone).toBe("+256700000111");
  });

  it("Duplicate phone -> returns existing client 200", async () => {
    // admin verify
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // For this test: clients.insert returns unique-violation; then clients.select returns existing
    (supabaseAdmin.from as unknown as jest.Mock).mockImplementationOnce(
      (table: string) => {
        if (table === "clients") {
          return {
            insert: (payload: any[]) => ({
              // Simulate PG unique violation
              select: async () => ({
                data: null,
                error: {
                  code: "23505",
                  message: "duplicate key value violates unique constraint",
                },
              }),
            }),
            select: () => ({
              eq: () => ({
                limit: () => ({
                  maybeSingle: async () => ({
                    data: {
                      id: "existing-client-id",
                      phone: "+256700000000",
                      name: "Existing",
                    },
                    error: null,
                  }),
                }),
              }),
            }),
          };
        }
        return {
          select: () => ({
            maybeSingle: async () => ({
              data: { id: "admin-uuid" },
              error: null,
            }),
          }),
        };
      }
    );

    const res = await app.inject({
      method: "POST",
      url: "/api/clients",
      headers: { Authorization: "Bearer dummy" },
      payload: {
        name: "Any",
        phone: "+256700000000",
        address: "Somewhere",
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].phone).toBe("+256700000000");
  });
});
