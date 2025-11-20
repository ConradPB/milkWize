import { FastifyInstance } from "fastify";
import { supabaseAdmin } from "../supabase";
import { isValidUuid } from "../utils";

export default async function paymentsRoutes(server: FastifyInstance) {
  // Create manual payment (admin records)
  server.post("/api/payments", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const body = (request.body || {}) as any;
      const { order_id, amount, method, txn_ref } = body;
      if (!order_id || amount == null || !method)
        return reply
          .status(400)
          .send({ error: "Missing order_id, amount or method" });
      if (!isValidUuid(String(order_id)))
        return reply.status(400).send({ error: "order_id must be UUID" });

      // Resolve admin id
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
        .from("payments")
        .insert([
          {
            order_id,
            amount,
            method,
            txn_ref,
            status: "pending",
            paid_at: null,
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

  // List payments (filter by order)
  server.get("/api/payments", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const q = request.query as any;
      const { order_id } = q;
      let query = supabaseAdmin.from("payments").select("*");
      if (order_id) {
        if (!isValidUuid(String(order_id)))
          return reply.status(400).send({ error: "order_id must be UUID" });
        query = query.eq("order_id", order_id);
      }

      const { data, error } = await query;
      if (error) return reply.status(500).send({ error: error.message });
      return reply.send({ data });
    } catch (err: any) {
      server.log.error(err);
      return reply.status(500).send({ error: err.message || "server error" });
    }
  });
}
