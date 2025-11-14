import { FastifyInstance } from 'fastify';
import { verifyWebhookHmac } from '../utils';


export default async function webhookRoutes(server: FastifyInstance) {
server.post('/api/webhook/payment', async (request, reply) => {
const sig = (request.headers['x-webhook-signature'] as string) || '';
const secret = process.env.WEBHOOK_SECRET || '';
const raw = JSON.stringify(request.body || {});
if (!verifyWebhookHmac(raw, sig, secret)) {
return reply.status(403).send({ error: 'Invalid signature' });
}


// TODO: implement payment processing: lookup order, update payments, insert audit_log
server.log.info({ msg: 'valid webhook', body: request.body });
return reply.send({ ok: true });
});
}