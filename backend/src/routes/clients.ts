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

      const body = (request.body || {}) as any;
      const { name, phone, address, preferred_window } = body;
      if (!name || !phone)
        return reply.status(400).send({ error: "Missing name or phone" });

      // Verify admin (map JWT -> admins row)
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error) {
        server.log.error({
          msg: "supabaseAdmin.auth.getUser failed",
          error: userRes.error,
        });
        return reply.status(403).send({ error: "Invalid user token" });
      }
      const userId = userRes.data?.user?.id;
      if (!userId)
        return reply.status(403).send({ error: "Invalid user token" });

      const { data: adminRow, error: adminError } = await supabaseAdmin
        .from("admins")
        .select("id")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();

      if (adminError) {
        server.log.error({ msg: "Error looking up admin", adminError });
        return reply.status(500).send({ error: "Server error" });
      }
      if (!adminRow)
        return reply.status(403).send({ error: "User not mapped to admin" });

      // Insert client
      const { data, error } = await supabaseAdmin
        .from("clients")
        .insert([
          {
            name,
            phone,
            address: address || null,
            preferred_window: preferred_window || "morning",
          },
        ])
        .select();

      if (error) {
        server.log.error({ msg: "Error inserting client", error });
        return reply.status(500).send({ error: error.message });
      }

      return reply.status(201).send({ data });
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
}
