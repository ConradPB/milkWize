````md
# milkWize Backend

Node + Fastify + TypeScript backend for milkWize. Includes basic endpoints:

- POST /api/milking_events — inserts milking_event (resolves admin id from user JWT via RPC)
- POST /api/webhook/payment — webhook stub with HMAC verification
- GET /health

## Quickstart

1. Copy `.env.example` to `.env` and fill in values
2. npm ci
3. npm run dev
4. Run tests: npm test

## Notes

- Create the `get_admin_id_from_jwt()` RPC in your Supabase project (we added earlier):

```sql
create or replace function get_admin_id_from_jwt() returns uuid language sql stable as $$
select id from admins where auth_uid = auth.uid()::text limit 1;
$$;
```
````

```

```
