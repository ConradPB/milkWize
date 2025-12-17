# milkWize Backend

MilkWize backend is a **Node.js + Fastify + TypeScript** API that powers the MilkWize system.  
It handles **clients, orders, milking events, payments**, and **secure webhooks**, using **Supabase** for authentication and database access.

The backend is production-ready, fully tested, and deployed on **Render**.

---

## Tech Stack

- **Node.js** (Node 22)
- **Fastify** (HTTP server)
- **TypeScript**
- **Supabase** (Auth + PostgreSQL)
- **Jest** (tests)
- **pnpm** (package manager)
- **Docker** (optional, supported)
- **Render** (deployment)

---

## Project Structure

```
MILKWIZE-backend/
│
├── backend/
│   ├── src/
│   │   ├── index.ts              # App entry point
│   │   ├── supabase.ts           # Supabase admin client
│   │   ├── utils.ts              # Shared helpers
│   │   │
│   │   ├── routes/
│   │   │   ├── clients.ts
│   │   │   ├── orders.ts
│   │   │   ├── milking.ts
│   │   │   ├── payments.ts
│   │   │   └── webhook.ts
│   │   │
│   │   └── tests/
│   │       ├── _mockSupabase.ts
│   │       ├── clients.create.test.ts
│   │       ├── milking.test.ts
│   │       ├── orders.confirm.test.ts
│   │       ├── utils.test.ts
│   │       └── webhook.test.ts
│   │
│   ├── dist/                     # Compiled output
│   ├── package.json
│   ├── tsconfig.json
│   ├── jest.config.js
│   ├── Dockerfile
│   ├── .env.example
│   ├── .gitignore
│   └── pnpm-lock.yaml
│
└── README.md
```

---

## Core Concepts

### Authentication

- Supabase handles **user authentication**
- API expects a **JWT** in the `Authorization` header:

```
Authorization: Bearer <JWT>
```

- Server validates JWT using **Supabase service-role client**

### Roles

- **Admins**
  - Create clients
  - Create orders
  - Record milking events
  - Record and update payments
- **Clients**
  - View own profile
  - View and confirm own orders

Strict access control is enforced at the API level.

---

## API Endpoints

### Health

- `GET /health` — service health check

### Clients

- `POST /api/clients` — create client (admin only)
- `GET /api/clients` — list / search clients (admin only)
- `GET /api/clients/me` — client gets own profile + orders
- `POST /api/clients/:id/link` — admin links auth user to client
- `POST /api/clients/link-self` — client self-links via phone match

### Orders

- `POST /api/orders` — create order (admin)
- `GET /api/orders` — list orders (admin)
- `PATCH /api/orders/:id/confirm` — client confirms own order

### Milking

- `POST /api/milking_events` — record milking event (admin)

### Payments

- `POST /api/payments` — record payment (admin)
- `GET /api/payments` — list payments
- `PUT /api/payments/:id` — update payment status (admin)

### Webhook

- `POST /api/webhook/payment` — HMAC-verified payment webhook

---

## Environment Variables

Create a `.env` file (or set variables in Render):

```
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=
SUPABASE_ANON_KEY=

WEBHOOK_SECRET=

CORS_ORIGIN=
RATE_LIMIT_DEFAULT_MAX=100
RATE_LIMIT_DEFAULT_WINDOW=1 minute

PORT=8080
NODE_ENV=development
```

### Notes

- `SUPABASE_SERVICE_ROLE_KEY` **must never be exposed publicly**
- `SUPABASE_ANON_KEY` is used for limited lookup flows
- `WEBHOOK_SECRET` secures webhook signatures

---

## Supabase Requirements

### Admin Mapping

Admins must exist in the `admins` table and be linked via:

```
admins.auth_uid = supabase_user.id
```

### Optional Helper Function

If needed:

```sql
create or replace function get_admin_id_from_jwt()
returns uuid
language sql stable as $$
  select id from admins
  where auth_uid = auth.uid()::text
  limit 1;
$$;
```

---

## Local Development

```bash
pnpm install
pnpm run dev
```

Build & run production version:

```bash
pnpm run build
pnpm start
```

---

## Testing

All backend logic is covered by Jest tests.

Run tests:

```
pnpm test
```

Tests include:

- Client creation
- Order confirmation
- Milking events
- Webhook signature validation
- Utility functions

All tests currently pass.

---

## Manual API Testing (Postman / curl)

Recommended flows to verify manually:

1. Sign in user via Supabase → get JWT
2. `GET /api/clients/me`  
   → expect 200 with client + orders
3. Admin:
   - `POST /api/orders`
   - Client confirms order
   - Verify order appears for client

---
