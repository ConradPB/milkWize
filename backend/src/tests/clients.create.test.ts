import Fastify from "fastify";
import clientsRoutes from "../routes/clients";

// Build a flexible mock for supabaseAdmin used by clients routes
const supabaseAdminMock = {
  auth: { getUser: jest.fn() },
  from: jest.fn(),
};

jest.mock("../supabase", () => ({ supabaseAdmin: supabaseAdminMock }));

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
    jest.resetAllMocks();
  });

  it("Insert new client -> 201", async () => {
    // admin auth
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // mock .from: first call will be clients.insert
    (supabaseAdmin.from as unknown as jest.Mock).mockImplementation(
      (table: string) => {
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
        return {
          select: () => ({
            maybeSingle: async () => ({ data: null, error: null }),
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
    // admin auth
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "caller-uuid" } },
      error: null,
    });

    // Simulate insert failing with duplicate then select returning existing
    (supabaseAdmin.from as unknown as jest.Mock).mockImplementation(
      (table: string) => {
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
        if (table === "clients") {
          return {
            insert: (payload: any[]) => ({
              select: async () => ({
                data: null,
                error: { code: "23505", message: "duplicate key" },
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
            maybeSingle: async () => ({ data: null, error: null }),
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
