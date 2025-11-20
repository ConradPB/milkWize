import { FastifyInstance } from 'fastify';
import { supabaseAdmin } from '../supabase';
import { isValidUuid } from '../utils';

export default async function ordersRoutes(server: FastifyInstance) {
  // Create order
  server.post('/api/orders', async (request, reply) => {
    try {
      const userJwt = (request.headers.authorization || '').replace('Bearer ', '').trim();
      if (!userJwt) return reply.status(401).send({ error: 'Missing JWT' });

      const body = (request.body || {}) as any;
      const { client_id, scheduled_date, scheduled_window, quantity_liters } = body;

      if (!client_id || !scheduled_date || quantity_liters == null) {
        return reply.status(400).send({ error: 'Missing required fields: client_id, scheduled_date, quantity_liters' });
      }
      if (!isValidUuid(String(client_id))) {
        return reply.status(400).send({ error: 'client_id must be a valid UUID' });
      }

      
  );
}
