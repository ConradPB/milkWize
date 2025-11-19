// src/routes/milking.ts
import { FastifyInstance } from 'fastify';
import { supabaseAdmin } from '../supabase';
import { isValidUuid } from '../utils';

export default async function milkingRoutes(server: FastifyInstance) {
  server.post('/api/milking_events', async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || '').replace('Bearer ', '').trim();
      if (!userJwt) return reply.status(401).send({ error: 'Missing JWT' });

      const body = (request.body || {}) as any;
      let { cow_id, cow_tag, milk_liters, milking_time } = body;

      if (!cow_id && !cow_tag) {
        return reply.status(400).send({ error: 'Provide either cow_id (UUID) or cow_tag' });
      }
      if (milk_liters == null || !milking_time) {
        return reply.status(400).send({ error: 'Missing milk_liters or milking_time' });
      }

      // If cow_tag provided, resolve it using anon key via direct supabase call (optional)
      if (!cow_id && cow_tag) {
        const anonKey = process.env.SUPABASE_ANON_KEY;
        if (!anonKey) {
          server.log.warn('SUPABASE_ANON_KEY not set; cannot resolve cow_tag');
          return reply.status(500).send({ error: 'Server misconfiguration: missing SUPABASE_ANON_KEY' });
        }
        // Use the REST API with anon key to look up cow by tag
        const res = await fetch(`${process.env.SUPABASE_URL}/rest/v1/cows?select=id,tag&tag=eq.${encodeURIComponent(cow_tag)}`, {
          method: 'GET',
          headers: {
            apikey: anonKey,
            Authorization: `Bearer ${userJwt}`,
            'Content-Type': 'application/json'
          }
        });
        if (!res.ok) {
          const txt = await res.text();
          server.log.error({ msg: 'Failed to resolve cow_tag', status: res.status, txt });
          return reply.status(500).send({ error: 'Failed to resolve cow_tag' });
        }
        const arr = await res.json();
        if (!Array.isArray(arr) || arr.length === 0) return reply.status(404).send({ error: 'cow_tag not found' });
        cow_id = arr[0].id;
      }

      // Validate cow_id
      if (!isValidUuid(String(cow_id))) {
        return reply.status(400).send({ error: 'cow_id must be a valid UUID', cow_id });
      }

      // Resolve user info via supabase admin client (service role)
      const userRes = await supabaseAdmin.auth.getUser(userJwt);
      if (userRes.error) {
        server.log.error({ msg: 'supabaseAdmin.auth.getUser failed', error: userRes.error });
        return reply.status(403).send({ error: 'Failed to resolve user from token' });
      }
      const userId = userRes.data?.user?.id;
      if (!userId) {
        server.log.error({ msg: 'No user id in supabase auth response', userRes });
        return reply.status(403).send({ error: 'Invalid user token' });
      }

      // Query admins table to find the mapped admin id (UUID)
      const { data: adminRows, error: adminError } = await supabaseAdmin
        .from('admins')
        .select('id,auth_uid')
        .eq('auth_uid', userId)
        .limit(1)
        .maybeSingle();

      if (adminError) {
        server.log.error({ msg: 'Error querying admins table', adminError });
        return reply.status(500).send({ error: 'Server failed to lookup admin' });
      }
      if (!adminRows) {
        return reply.status(403).send({ error: 'User not mapped to admin' });
      }

      const adminId = (adminRows as any).id;
      if (!isValidUuid(String(adminId))) {
        server.log.error({ msg: 'Resolved admin id not uuid', adminId });
        return reply.status(400).send({ error: 'Resolved admin id is not a valid UUID', adminId });
      }

      server.log.info({
        msg: 'Inserting milking_event - resolved values (server-resolve)',
        cow_id,
        cow_id_type: typeof cow_id,
        adminId,
        adminId_type: typeof adminId,
        milk_liters,
        milking_time
      });

      
  });
}
