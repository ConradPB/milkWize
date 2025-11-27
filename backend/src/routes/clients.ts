import { FastifyInstance } from "fastify";
import { supabaseAdmin } from "../supabase";
import { isValidUuid } from "../utils";

export default async function clientsRoutes(server: FastifyInstance) {
  // Create client (admin only)
  server.post("/api/clients", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const { name, phone, address, preferred_window } = request.body as any;
      if (!name || !phone)
        return reply.status(400).send({ error: "Missing name or phone" });

      // Verify admin
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error)
        return reply.status(403).send({ error: "Invalid user token" });
      const userId = userRes.data?.user?.id;

      const { data: adminRow, error: adminError } = await supabaseAdmin
        .from("admins")
        .select("id")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();

      if (adminError) return reply.status(500).send({ error: "Server error" });
      if (!adminRow)
        return reply.status(403).send({ error: "User not mapped to admin" });

      const insertPayload = {
        name,
        phone,
        address: address || null,
        preferred_window: preferred_window || "morning",
      };

      // Try insert, fallback to existing if unique constraint violation
      try {
        const { data, error } = await supabaseAdmin
          .from("clients")
          .insert([insertPayload])
          .select();

        if (error) {
          // Handle duplicate phone
          if (
            (error as any)?.code === "23505" ||
            error.message.includes("duplicate key")
          ) {
            const { data: existing, error: findErr } = await supabaseAdmin
              .from("clients")
              .select("*")
              .eq("phone", phone)
              .limit(1)
              .maybeSingle();

            if (findErr)
              return reply.status(500).send({ error: "Server error" });
            return reply.status(200).send({ data: [existing] });
          }
          return reply.status(500).send({ error: error.message });
        }

        return reply.status(201).send({ data });
      } catch (e: any) {
        // fallback: return existing client
        const { data: existing } = await supabaseAdmin
          .from("clients")
          .select("*")
          .eq("phone", phone)
          .limit(1)
          .maybeSingle();
        return reply.status(200).send({ data: [existing] });
      }
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // List clients (filterable)
  server.get("/api/clients", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const q = request.query as any;
      const { phone, name } = q;

      let query = supabaseAdmin.from("clients").select("*");

      if (phone) query = query.eq("phone", phone);
      if (name) query = query.ilike("name", `%${name}%`);

      const { data, error } = await query;
      if (error) return reply.status(500).send({ error: error.message });
      return reply.send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // Update client
  server.put("/api/clients/:id", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid client id" });

      const body = (request.body || {}) as any;
      const allowed = ["name", "phone", "address", "preferred_window"];
      const updates: any = {};
      for (const k of allowed) if (k in body) updates[k] = body[k];

      if (Object.keys(updates).length === 0)
        return reply.status(400).send({ error: "No valid fields to update" });

      // Verify admin
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error)
        return reply.status(403).send({ error: "Invalid user token" });
      const userId = userRes.data?.user?.id;
      const { data: adminRow } = await supabaseAdmin
        .from("admins")
        .select("id")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();
      if (!adminRow)
        return reply.status(403).send({ error: "User not mapped to admin" });

      const { data, error } = await supabaseAdmin
        .from("clients")
        .update(updates)
        .eq("id", id)
        .select();
      if (error) return reply.status(500).send({ error: error.message });
      return reply.send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // Delete client
  server.delete("/api/clients/:id", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid client id" });

      // Verify admin
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error)
        return reply.status(403).send({ error: "Invalid user token" });
      const userId = userRes.data?.user?.id;
      const { data: adminRow } = await supabaseAdmin
        .from("admins")
        .select("id")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();
      if (!adminRow)
        return reply.status(403).send({ error: "User not mapped to admin" });

      const { error } = await supabaseAdmin
        .from("clients")
        .delete()
        .eq("id", id);
      if (error) return reply.status(500).send({ error: error.message });
      return reply.send({ ok: true });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // ---------------------------
  // Admin: Link an auth_user_id to a client (safe admin-only endpoint)
  // POST /api/clients/:id/link
  // Body: { "auth_user_id": "<uuid>" }
  // ---------------------------
  // Admin: Link an auth_user_id to a client (safe admin-only endpoint)
  server.post("/api/clients/:id/link", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      // Admin check
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error)
        return reply.status(403).send({ error: "Invalid user token" });
      const userId = userRes.data?.user?.id;
      const { data: adminRow } = await supabaseAdmin
        .from("admins")
        .select("id")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();
      if (!adminRow)
        return reply.status(403).send({ error: "User not mapped to admin" });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid client id" });

      const body = (request.body || {}) as any;
      const { auth_user_id } = body;
      if (!auth_user_id || !isValidUuid(String(auth_user_id)))
        return reply.status(400).send({ error: "Invalid auth_user_id" });

      // Check the auth user actually exists in Supabase (query auth.users)
      const { data: targetUserRow, error: targetUserErr } = await supabaseAdmin
        .from("auth.users")
        .select("id")
        .eq("id", auth_user_id)
        .limit(1)
        .maybeSingle();

      if (targetUserErr) {
        server.log.error({ msg: "Error querying auth.users", targetUserErr });
        return reply.status(500).send({ error: "Server error" });
      }
      if (!targetUserRow) {
        return reply.status(404).send({ error: "Auth user not found" });
      }

      // Ensure no other client is linked to this auth_user_id
      const { data: existing, error: existErr } = await supabaseAdmin
        .from("clients")
        .select("id")
        .eq("auth_user_id", auth_user_id)
        .limit(1)
        .maybeSingle();
      if (existErr) return reply.status(500).send({ error: existErr.message });
      if (existing)
        return reply
          .status(409)
          .send({ error: "auth_user_id already linked to another client" });

      // Update client row
      const { data, error } = await supabaseAdmin
        .from("clients")
        .update({ auth_user_id })
        .eq("id", id)
        .select();

      if (error) return reply.status(500).send({ error: error.message });
      return reply.status(200).send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });
}
