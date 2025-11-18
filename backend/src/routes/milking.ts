import { FastifyInstance } from "fastify";
import fetch from "node-fetch";
import { supabaseAdmin } from "../supabase";
import { isValidUuid } from "../utils";

export default async function milkingRoutes(server: FastifyInstance) {
  server.post("/api/milking_events", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const body = request.body as any;
      let { cow_id, cow_tag, milk_liters, milking_time } = body || {};

      if (!cow_id && !cow_tag) {
        return reply
          .status(400)
          .send({ error: "Provide either cow_id (UUID) or cow_tag" });
      }

      // if cow_tag provided resolve to cow_id via Supabase (anon request using anon key)
      if (!cow_id && cow_tag) {
        const anonKey = process.env.SUPABASE_ANON_KEY;
        if (!anonKey) {
          server.log.warn("SUPABASE_ANON_KEY not set; cannot resolve cow_tag");
          return reply
            .status(500)
            .send({ error: "Server missing configuration to resolve cow_tag" });
        }
        const res = await fetch(
          `${process.env.SUPABASE_URL}/rest/v1/cows?select=id,tag&tag=eq.${encodeURIComponent(cow_tag)}`,
          {
            method: "GET",
            headers: {
              apikey: anonKey,
              Authorization: `Bearer ${userJwt}`,
              "Content-Type": "application/json",
            },
          }
        );
        if (!res.ok) {
          const txt = await res.text();
          server.log.error({
            msg: "Failed to resolve cow_tag",
            status: res.status,
            txt,
          });
          return reply.status(500).send({ error: "Failed to resolve cow_tag" });
        }
        const arr = await res.json();
        if (!Array.isArray(arr) || arr.length === 0)
          return reply.status(404).send({ error: "cow_tag not found" });
        cow_id = arr[0].id;
      }

      // Validate cow_id is a UUID
      if (!isValidUuid(String(cow_id))) {
        return reply.status(400).send({ error: "cow_id must be a valid UUID" });
      }

      if (milk_liters == null || !milking_time) {
        return reply
          .status(400)
          .send({ error: "Missing milk_liters or milking_time" });
      }

      // Resolve admin id via RPC
      const rpcUrl = `${process.env.SUPABASE_URL}/rest/v1/rpc/get_admin_id_from_jwt`;
      const res = await fetch(rpcUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${userJwt}`,
          apikey: process.env.SUPABASE_SERVICE_ROLE_KEY || "",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({}),
      });

      if (!res.ok) {
        const txt = await res.text();
        request.log.error({
          msg: "Failed to resolve admin id",
          status: res.status,
          txt,
        });
        return reply.status(403).send({ error: "Failed to resolve admin id" });
      }
      const json = (await res.json()) as any[];
      const adminId = json?.[0] ? Object.values(json[0])[0] : null;
      if (!adminId)
        return reply.status(403).send({ error: "User not mapped to admin" });

      const { data, error } = await supabaseAdmin
        .from("milking_events")
        .insert([
          {
            cow_id,
            recorded_by: adminId,
            milk_liters,
            milking_time,
          },
        ]);

      if (error) {
        request.log.error({ error });
        return reply.status(500).send({ error: error.message });
      }

      return reply.status(201).send({ data });
    } catch (err: any) {
      request.log.error(err);
      return reply.status(500).send({ error: err.message || "unknown error" });
    }
  });
}
