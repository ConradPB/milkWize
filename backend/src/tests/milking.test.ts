import { supabaseAdmin } from '../supabase';


jest.mock('../supabase', () => ({
supabaseAdmin: {
from: jest.fn(() => ({
insert: jest.fn(() => ({ data: [{ id: 'fake' }], error: null })),
})),
},
}));


import milkingRoutes from '../routes/milking';
import Fastify from 'fastify';


describe('milking route', () => {
let app: any;
beforeAll(async () => {
app = Fastify();
app.register(require('fastify-formbody'));
await app.register(milkingRoutes);
});
afterAll(() => app.close());


it('returns 401 without JWT', async () => {
const res = await app.inject({ method: 'POST', url: '/api/milking_events', payload: {} });
expect(res.statusCode).toBe(401);
});
});