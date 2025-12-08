import Fastify from "fastify";
import clientsRoutes from "../routes/clients";

jest.mock("../supabase", () => {
  return {
    supabaseAdmin: {
      auth: { getUser: jest.fn() },
      from: jest.fn(),
    },
  };
});

import { supabaseAdmin } from "../supabase";

describe("POST /api/clients", () => {
  let server: ReturnType<typeof Fastify>;
  beforeEach(async () => {
    server = Fastify({ logger: false });
    await server.register(clientsRoutes);
    await server.ready();
    jest.clearAllMocks();
  });
  afterEach(async () => {
    await server.close();
  });

  test("Insert new client -> 201", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "admin-uid" } },
      error: null,
    });
    (supabaseAdmin.from as jest.Mock).mockReturnValue({
      select: () => ({
        eq: () => ({
          limit: () => ({
            maybeSingle: async () => ({
              data: { id: "admin-row" },
              error: null,
            }),
          }),
        }),
      }),
    });
    // Insert flow returns data
    (supabaseAdmin.from as jest.Mock).mockReturnValueOnce({
      insert: () => ({
        select: async () => ({
          data: [{ id: "client-1", name: "X" }],
          error: null,
        }),
      }),
    });

    const res = await server.inject({
      method: "POST",
      url: "/api/clients",
      headers: { Authorization: "Bearer dummy" },
      payload: { name: "X", phone: "+256700000000" },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.data)).toBe(true);
  });

  test("Duplicate phone -> returns existing client 200", async () => {
    (supabaseAdmin.auth.getUser as jest.Mock).mockResolvedValue({
      data: { user: { id: "admin-uid" } },
      error: null,
    });
    // First insert returns error with duplicate
    (supabaseAdmin.from as jest.Mock).mockReturnValueOnce({
      insert: () => ({
        select: async () => ({
          data: null,
          error: { message: "duplicate key", code: "23505" },
        }),
      }),
    });
    // Then the find by phone returns the existing client
    (supabaseAdmin.from as jest.Mock).mockReturnValueOnce({
      select: () => ({
        eq: () => ({
          limit: () => ({
            maybeSingle: async () => ({
              data: { id: "client-existing", phone: "+256700000000" },
              error: null,
            }),
          }),
        }),
      }),
    });

    const res = await server.inject({
      method: "POST",
      url: "/api/clients",
      headers: { Authorization: "Bearer dummy" },
      payload: { name: "X", phone: "+256700000000" },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data[0].phone).toBe("+256700000000");
  });
});
