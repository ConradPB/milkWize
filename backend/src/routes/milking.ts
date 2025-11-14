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


