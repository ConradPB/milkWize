import { FastifyInstance } from "fastify";
import { supabaseAdmin } from "../supabase";
import { isValidUuid } from "../utils";

/** resolve admin id from JWT (same pattern as clients) */
async function resolveAdminId(userJwt?: string | null) {
  if (!userJwt) return { ok: false, code: 401, msg: "Missing JWT" };
  const userRes = await supabaseAdmin.auth.getUser(userJwt);
  if (userRes.error) return { ok: false, code: 403, msg: "Invalid user token" };
  const userId = userRes.data?.user?.id;
  if (!userId) return { ok: false, code: 403, msg: "Invalid user token" };

  const { data: adminRow, error } = await supabaseAdmin
    .from("admins")
    .select("id")
    .eq("auth_uid", userId)
    .limit(1)
    .maybeSingle();
  if (error) return { ok: false, code: 500, msg: "Server error" };
  if (!adminRow)
    return { ok: false, code: 403, msg: "User not mapped to admin" };
  return { ok: true, adminId: adminRow.id };
}

export default async function milkingRoutes(server: FastifyInstance) {
  server.post("/api/milking_events", async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || "")
        .replace("Bearer ", "")
        .trim();
      if (!userJwt) return reply.status(401).send({ error: "Missing JWT" });

      const body = (request.body || {}) as any;
      let { cow_id, cow_tag, milk_liters, milking_time } = body;

      if (!cow_id && !cow_tag) {
        return reply
          .status(400)
          .send({ error: "Provide either cow_id (UUID) or cow_tag" });
      }

      // Validate milking_time presence (expecting ISO string or timestamp)
      if (milking_time == null || String(milking_time).trim() === "") {
        return reply
          .status(400)
          .send({ error: "Missing milking_time (ISO string or timestamp)" });
      }

      // Coerce milk_liters to number and validate
      if (milk_liters == null) {
        return reply.status(400).send({ error: "Missing milk_liters" });
      }
      const milkLitersNum = Number(milk_liters);
      if (Number.isNaN(milkLitersNum)) {
        return reply
          .status(400)
          .send({ error: "milk_liters must be a number" });
      }

      // If cow_tag provided, resolve it using anon key via direct supabase call (optional)
      if (!cow_id && cow_tag) {
        const anonKey = process.env.SUPABASE_ANON_KEY;
        if (!anonKey) {
          server.log.warn("SUPABASE_ANON_KEY not set; cannot resolve cow_tag");
          return reply.status(500).send({
            error: "Server misconfiguration: missing SUPABASE_ANON_KEY",
          });
        }
        const res = await fetch(
          `${process.env.SUPABASE_URL}/rest/v1/cows?select=id,tag&tag=eq.${encodeURIComponent(
            cow_tag
          )}`,
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

      // Validate cow_id uuid
      if (!isValidUuid(String(cow_id))) {
        return reply
          .status(400)
          .send({ error: "cow_id must be a valid UUID", cow_id });
      }

      // Resolve admin id
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error) {
        server.log.error({
          msg: "supabaseAdmin.auth.getUser failed",
          error: userRes.error,
        });
        return reply
          .status(403)
          .send({ error: "Failed to resolve user from token" });
      }
      const userId = userRes.data?.user?.id;
      if (!userId) {
        server.log.error({
          msg: "No user id in supabase auth response",
          userRes,
        });
        return reply.status(403).send({ error: "Invalid user token" });
      }

      const { data: adminRows, error: adminError } = await supabaseAdmin
        .from("admins")
        .select("id,auth_uid")
        .eq("auth_uid", userId)
        .limit(1)
        .maybeSingle();

      if (adminError) {
        server.log.error({ msg: "Error querying admins table", adminError });
        return reply
          .status(500)
          .send({ error: "Server failed to lookup admin" });
      }
      if (!adminRows) {
        return reply.status(403).send({ error: "User not mapped to admin" });
      }

      const adminId = (adminRows as any).id;

      // Insert milking_event and return the inserted row(s)
      const { data, error } = await supabaseAdmin
        .from("milking_events")
        .insert([
          {
            cow_id,
            recorded_by: adminId,
            milk_liters: milkLitersNum,
            milking_time, // keep same type as body (ISO string recommended)
          },
        ])
        .select();

      if (error) {
        server.log.error({ msg: "Supabase insert error", error });
        return reply.status(500).send({ error: error.message });
      }

      return reply.status(201).send({ data });
    } catch (err: any) {
      request.log.error(err);
      return reply.status(500).send({ error: err.message || "unknown error" });
    }
  });
}
