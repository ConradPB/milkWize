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

export default async function milkingRoutes(server: FastifyInstance) {}
