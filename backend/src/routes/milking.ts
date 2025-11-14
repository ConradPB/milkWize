import { FastifyInstance } from 'fastify';
import fetch from 'node-fetch';
import { supabaseAdmin } from '../supabase';


export default async function milkingRoutes(server: FastifyInstance) {
server.post('/api/milking_events', async (request, reply) => {
const userJwt = (request.headers.authorization || '').replace('Bearer ', '').trim();
if (!userJwt) return reply.status(401).send({ error: 'Missing JWT' });


const body = request.body as any;
const { cow_id, milk_liters, milking_time } = body || {};
if (!cow_id || milk_liters == null || !milking_time) {
return reply.status(400).send({ error: 'Missing fields' });
}


// Resolve admin id via RPC function that uses auth.uid() evaluated against the provided JWT
const rpcUrl = `${process.env.SUPABASE_URL}/rest/v1/rpc/get_admin_id_from_jwt`;
const res = await fetch(rpcUrl, {
method: 'POST',
headers: {
Authorization: `Bearer ${userJwt}`,
apikey: process.env.SUPABASE_SERVICE_ROLE_KEY || '',
'Content-Type': 'application/json',
},
});


if (!res.ok) {
const text = await res.text();
request.log.error({ msg: 'Failed to resolve admin id', status: res.status, text });
return reply.status(403).send({ error: 'Failed to resolve admin id' });
}


const json = (await res.json()) as any[];
const adminId = json?.[0] ? Object.values(json[0])[0] : null;
if (!adminId) return reply.status(403).send({ error: 'User not mapped to admin' });


const { data, error } = await supabaseAdmin.from('milking_events').insert([
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
});
}