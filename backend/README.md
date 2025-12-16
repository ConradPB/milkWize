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
