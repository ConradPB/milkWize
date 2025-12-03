import { FastifyInstance } from "fastify";
import { supabaseAdmin } from "../supabase";
import { isValidUuid } from "../utils";

export default async function ordersRoutes(server: FastifyInstance) {
  // Create order
  server.post("/api/orders", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const body = (request.body || {}) as any;
      const { client_id, scheduled_date, scheduled_window, quantity_liters } =
        body;

      if (!client_id || !scheduled_date || quantity_liters == null) {
        return reply.status(400).send({
          error:
            "Missing required fields: client_id, scheduled_date, quantity_liters",
        });
      }
      if (!isValidUuid(String(client_id))) {
        return reply
          .status(400)
          .send({ error: "client_id must be a valid UUID" });
      }

      // Resolve user -> admin id
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error)
        return reply.status(403).send({ error: "Invalid user token" });
      const userId = userRes.data?.user?.id;
      if (!userId)
        return reply.status(403).send({ error: "Invalid user token" });

      const { data: adminRow, error: adminError } = await supabaseAdmin
        .from("admins")
        .select("id,auth_uid")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();

      if (adminError)
        return reply.status(500).send({ error: "Failed to lookup admin" });
      if (!adminRow)
        return reply.status(403).send({ error: "User not mapped to admin" });

      const adminId = (adminRow as any).id;

      const { data, error } = await supabaseAdmin
        .from("orders")
        .insert([
          {
            client_id,
            created_by: adminId,
            scheduled_date,
            scheduled_window,
            quantity_liters,
            status: "pending",
          },
        ])
        .select();

      if (error) return reply.status(500).send({ error: error.message });
      return reply.status(201).send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // List orders (filter by client_id, status, date)
  server.get("/api/orders", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const q = request.query as any;
      const { client_id, status, scheduled_date } = q;

      // build filter
      let query = supabaseAdmin.from("orders").select("*");

      if (client_id) {
        if (!isValidUuid(String(client_id)))
          return reply.status(400).send({ error: "client_id must be UUID" });
        query = query.eq("client_id", client_id);
      }
      if (status) query = query.eq("status", status);
      if (scheduled_date) query = query.eq("scheduled_date", scheduled_date);

      // Use service role to read
      const { data, error } = await query;
      if (error) return reply.status(500).send({ error: error.message });
      return reply.send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // Update order
  server.put("/api/orders/:id", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid order id" });

      const body = (request.body || {}) as any;
      const allowed = [
        "scheduled_date",
        "scheduled_window",
        "quantity_liters",
        "status",
      ];
      const updates: any = {};
      for (const k of allowed) if (k in body) updates[k] = body[k];

      if (Object.keys(updates).length === 0)
        return reply.status(400).send({ error: "No valid fields to update" });

      // Resolve admin (permission)
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
        .from("orders")
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

  // Delete order
  server.delete("/api/orders/:id", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid order id" });

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
        .from("orders")
        .delete()
        .eq("id", id);
      if (error) return reply.status(500).send({ error: error.message });
      return reply.send({ ok: true });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });

  // PATCH /api/orders/:id/confirm  (client calls this)
  server.patch("/api/orders/:id/confirm", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const { id } = request.params as any;
      if (!isValidUuid(String(id)))
        return reply.status(400).send({ error: "Invalid order id" });

      // Validate JWT and get caller uid
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error) {
        server.log.error({
          msg: "supabaseAdmin.auth.getUser failed",
          error: userRes.error,
        });
        return reply.status(403).send({ error: "Invalid user token" });
      }
      const callerUid = userRes.data?.user?.id;
      if (!callerUid)
        return reply.status(403).send({ error: "Invalid user token" });

      // Call RPC that enforces ownership and updates the order
      const { data, error } = await supabaseAdmin.rpc("confirm_order", {
        _order_id: id,
        _caller: callerUid,
      });

      if (error) {
        server.log.error({ msg: "RPC confirm_order failed", error });
        return reply.status(500).send({ error: "Server error" });
      }

      // RPC returns an array of updated rows (or empty array)
      const updated = Array.isArray(data) ? data[0] : data;
      if (!updated)
        return reply
          .status(403)
          .send({ error: "Not allowed or order not found" });

      return reply.status(200).json({ data: updated });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).json({ error: err.message || "server error" });
    }
  });
}
