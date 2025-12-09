import { FastifyInstance } from "fastify";
import { supabaseAdmin } from "../supabase";
import { isValidUuid } from "../utils";

/** small helper to resolve and verify admin; returns {ok, code, msg, adminId?} */
async function ensureAdmin(userJwt?: string | null) {
  if (!userJwt) return { ok: false, code: 401, msg: "Missing JWT" };
  const userRes = await supabaseAdmin.auth.getUser(userJwt);
  if (userRes.error) return { ok: false, code: 403, msg: "Invalid user token" };
  const userId = userRes.data?.user?.id;
  if (!userId) return { ok: false, code: 403, msg: "Invalid user token" };

  const { data: adminRow, error: adminError } = await supabaseAdmin
    .from("admins")
    .select("id")
    .eq("auth_uid", userId)
    .limit(1)
    .maybeSingle();

  if (adminError) return { ok: false, code: 500, msg: "Server error" };
  if (!adminRow)
    return { ok: false, code: 403, msg: "User not mapped to admin" };
  return { ok: true, adminId: adminRow.id };
}

export default async function clientsRoutes(server: FastifyInstance) {
  // Create client (admin only)
  server.post("/api/clients", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      const auth = await ensureAdmin(userJwt);
      if (!auth.ok) return reply.status(auth.code).send({ error: auth.msg });

      const { name, phone, address, preferred_window } = request.body as any;
      if (!name || !phone)
        return reply.status(400).send({ error: "Missing name or phone" });

      const insertPayload = {
        name,
        phone,
        address: address || null,
        preferred_window: preferred_window || "morning",
      };

      try {
        const { data, error } = await supabaseAdmin
          .from("clients")
          .insert([insertPayload])
          .select();

        if (error) {
          const pgCode = (error as any)?.code || "";
          if (
            pgCode === "23505" ||
            (error.message && error.message.includes("duplicate key"))
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

  // List clients - ADMIN ONLY (changed to require admin)
  server.get("/api/clients", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      const auth = await ensureAdmin(userJwt);
      if (!auth.ok) return reply.status(auth.code).send({ error: auth.msg });

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
      const auth = await ensureAdmin(userJwt);
      if (!auth.ok) return reply.status(auth.code).send({ error: auth.msg });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid client id" });

      const body = (request.body || {}) as any;
      const allowed = ["name", "phone", "address", "preferred_window"];
      const updates: any = {};
      for (const k of allowed) if (k in body) updates[k] = body[k];

      if (Object.keys(updates).length === 0)
        return reply.status(400).send({ error: "No valid fields to update" });

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
      const auth = await ensureAdmin(userJwt);
      if (!auth.ok) return reply.status(auth.code).send({ error: auth.msg });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid client id" });

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

  // Admin: Link auth_user_id -> client (POST /api/clients/:id/link)
  server.post("/api/clients/:id/link", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      const auth = await ensureAdmin(userJwt);
      if (!auth.ok) return reply.status(auth.code).send({ error: auth.msg });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid client id" });

      const { auth_user_id } = (request.body || {}) as any;
      if (!auth_user_id || !isValidUuid(String(auth_user_id)))
        return reply.status(400).send({ error: "Invalid auth_user_id" });

      // check auth user exists via RPC
      const { data: targetUserRow, error: targetUserErr } =
        await supabaseAdmin.rpc("get_auth_user_by_id", { _id: auth_user_id });
      if (targetUserErr) {
        server.log.error({
          msg: "Error querying auth.users via RPC",
          targetUserErr,
        });
        return reply.status(500).send({ error: "Server error" });
      }
      if (
        !targetUserRow ||
        (Array.isArray(targetUserRow) && targetUserRow.length === 0)
      ) {
        return reply.status(404).send({ error: "Auth user not found" });
      }

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

  // Client self-link: POST /api/clients/link-self
  server.post("/api/clients/link-self", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error)
        return reply.status(403).send({ error: "Invalid user token" });
      const callerUid = userRes.data?.user?.id;
      if (!callerUid)
        return reply.status(403).send({ error: "Invalid user token" });

      const { phone, client_id } = (request.body || {}) as any;
      if (!phone && !client_id)
        return reply.status(400).send({ error: "Provide phone or client_id" });

      let clientRow: any = null;
      if (client_id) {
        if (!isValidUuid(String(client_id)))
          return reply.status(400).send({ error: "Invalid client_id" });
        const { data, error } = await supabaseAdmin
          .from("clients")
          .select("*")
          .eq("id", client_id)
          .limit(1)
          .maybeSingle();
        if (error) return reply.status(500).send({ error: error.message });
        clientRow = data;
      } else {
        const { data, error } = await supabaseAdmin
          .from("clients")
          .select("*")
          .eq("phone", phone)
          .limit(1)
          .maybeSingle();
        if (error) return reply.status(500).send({ error: error.message });
        clientRow = data;
      }

      if (!clientRow)
        return reply.status(404).send({ error: "client row not found" });
      if (clientRow.auth_user_id)
        return reply.status(409).send({ error: "client already linked" });

      const { data, error } = await supabaseAdmin
        .from("clients")
        .update({ auth_user_id: callerUid })
        .eq("id", clientRow.id)
        .select();
      if (error) return reply.status(500).send({ error: error.message });
      return reply.status(200).send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // Client: get own client row + orders summary - unchanged
  server.get("/api/clients/me", async (request, reply) => {
    try {
      const rawAuth = (request.headers.authorization || "").trim();
      if (!rawAuth)
        return reply
          .status(401)
          .send({ error: "Missing Authorization header" });

      const userJwt = rawAuth.replace(/^Bearer\s+/i, "").trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      // prefer service-client resolution, fallback to decode
      let callerUid: string | null = null;
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (!userRes.error && userRes.data?.user?.id)
        callerUid = userRes.data.user.id;
      if (!callerUid) {
        try {
          const parts = userJwt.split(".");
          if (parts.length >= 2) {
            const payload = JSON.parse(
              Buffer.from(parts[1], "base64").toString("utf8")
            );
            if (payload?.sub) callerUid = String(payload.sub);
          }
        } catch (e) {}
      }
      if (!callerUid)
        return reply.status(403).send({ error: "Invalid or unresolvable JWT" });

      const { data: client, error: clientErr } = await supabaseAdmin
        .from("clients")
        .select("*")
        .eq("auth_user_id", callerUid)
        .limit(1)
        .maybeSingle();
      if (clientErr) return reply.status(500).send({ error: "Server error" });
      if (!client) return reply.status(404).send({ error: "client not found" });

      const { data: orders, error: ordersErr } = await supabaseAdmin
        .from("orders")
        .select("*")
        .eq("client_id", client.id);
      if (ordersErr) return reply.status(500).send({ error: "Server error" });

      return reply.status(200).send({ client, orders });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });
}
