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

      const body = (request.body as any) || {};
      let { cow_id, cow_tag, milk_liters, milking_time } = body;

      // basic presence checks
      if (!cow_id && !cow_tag) {
        return reply
          .status(400)
          .send({ error: "Provide either cow_id (UUID) or cow_tag" });
      }
      if (milk_liters == null || !milking_time) {
        return reply
          .status(400)
          .send({ error: "Missing milk_liters or milking_time" });
      }

      // If client provided cow_tag, resolve to cow_id (requires SUPABASE_ANON_KEY)
      if (!cow_id && cow_tag) {
        const anonKey = process.env.SUPABASE_ANON_KEY;
        if (!anonKey) {
          server.log.warn("SUPABASE_ANON_KEY not set; cannot resolve cow_tag");
          return reply
            .status(500)
            .send({ error: "Server missing configuration to resolve cow_tag" });
        }

        const tagUrl = `${process.env.SUPABASE_URL}/rest/v1/cows?select=id,tag&tag=eq.${encodeURIComponent(cow_tag)}`;
        const res = await fetch(tagUrl, {
          method: "GET",
          headers: {
            apikey: anonKey,
            Authorization: `Bearer ${userJwt}`,
            "Content-Type": "application/json",
          },
        });

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
        if (!Array.isArray(arr) || arr.length === 0) {
          return reply.status(404).send({ error: "cow_tag not found" });
        }
        cow_id = arr[0].id;
      }

      // Validate that cow_id is a UUID
      if (!isValidUuid(String(cow_id))) {
        return reply
          .status(400)
          .send({ error: "cow_id must be a valid UUID", cow_id });
      }

      // Resolve admin id via RPC
      const rpcUrl = `${process.env.SUPABASE_URL}/rest/v1/rpc/get_admin_id_from_jwt`;
      const rpcRes = await fetch(rpcUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${userJwt}`,
          apikey: process.env.SUPABASE_SERVICE_ROLE_KEY || "",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({}),
      });

      if (!rpcRes.ok) {
        const txt = await rpcRes.text();
        request.log.error({
          msg: "Failed to resolve admin id",
          status: rpcRes.status,
          txt,
        });
        return reply.status(403).send({ error: "Failed to resolve admin id" });
      }

      const rpcJson = (await rpcRes.json()) as any[];
      const adminId = rpcJson?.[0] ? Object.values(rpcJson[0])[0] : null;

      // Log resolved values (helps debug exactly what's being sent to Postgres)
      request.log.info({
        msg: "Inserting milking_event - resolved values",
        cow_id,
        cow_id_type: typeof cow_id,
        adminId,
        adminId_type: typeof adminId,
        milk_liters,
        milking_time,
      });

      // Validate adminId is a UUID
      if (!isValidUuid(String(adminId))) {
        return reply
          .status(400)
          .send({ error: "Resolved admin id is not a valid UUID", adminId });
      }

      // Final insert (service role)
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
        request.log.error({ msg: "Supabase insert error", error });
        return reply.status(500).send({ error: error.message });
      }

      return reply.status(201).send({ data });
    } catch (err: any) {
      request.log.error(err);
      return reply.status(500).send({ error: err.message || "unknown error" });
    }
  });
}
