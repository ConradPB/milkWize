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
