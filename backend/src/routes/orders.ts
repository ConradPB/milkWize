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
}
