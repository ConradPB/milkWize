import Fastify from "fastify";
import helmet from "fastify-helmet";
import formbody from "fastify-formbody";
import dotenv from "dotenv";
import rateLimit from "@fastify/rate-limit";
import cors from "@fastify/cors";

import milkingRoutes from "./routes/milking";
import webhookRoutes from "./routes/webhook";
import ordersRoutes from "./routes/orders";
import paymentsRoutes from "./routes/payments";
import clientsRoutes from "./routes/clients";

dotenv.config();

const server = Fastify({ logger: true });

// --- Basic middleware & security headers
server.register(helmet);
server.register(formbody);

// --- CORS (configurable)
const CORS_ORIGIN = process.env.CORS_ORIGIN || ""; // e.g. https://app.mydomain.com or * for dev
await server.register(cors, {
  origin: (origin, cb) => {
    // allow requests with no origin (mobile apps, curl, etc)
    if (!origin) return cb(null, true);
    if (!CORS_ORIGIN || CORS_ORIGIN === "*") return cb(null, true);
    // support comma-separated list
    const allowed = CORS_ORIGIN.split(",").map((s) => s.trim());
    if (allowed.includes(origin)) return cb(null, true);
    return cb(new Error("Not allowed by CORS"));
  },
  methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
  allowedHeaders: ["Content-Type", "Authorization", "x-webhook-signature"],
  credentials: true,
});

// --- Rate limiting (global defaults)
await server.register(rateLimit, {
  max: Number(process.env.RATE_LIMIT_DEFAULT_MAX || 100),
  timeWindow: process.env.RATE_LIMIT_DEFAULT_WINDOW || "1 minute",
  keyGenerator: (req) =>
    String(req.headers["x-forwarded-for"] || req.ip || "unknown"),
  errorResponseBuilder: (req, context) => {
    const afterMs = Number((context as any)?.after || 0);
    const retrySec = Math.ceil(afterMs / 1000);
    return {
      statusCode: 429,
      error: "Too Many Requests",
      message: `Rate limit exceeded, retry in ${retrySec}s`,
    };
  },
});

// --- Internal IP whitelist & route guard
// Set INTERNAL_IPS env to a comma-separated list of allowed internal IPs (e.g. 10.0.0.1,192.168.1.2)
const INTERNAL_IPS = (process.env.INTERNAL_IPS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
// Webhook IP whitelist (optional) — providers often give static IPs you can trust
const WEBHOOK_IP_WHITELIST = (process.env.WEBHOOK_IP_WHITELIST || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

// Helper to check if an IP is in a whitelist
function ipIsWhitelisted(ip: string | undefined, whitelist: string[]) {
  if (!ip) return false;
  // Fast path: exact match
  if (whitelist.includes(ip)) return true;
  // If client behind proxy, x-forwarded-for may have multiple IPs - check first
  const maybe = String(ip)
    .split(",")
    .map((s) => s.trim())[0];
  return whitelist.includes(maybe);
}

// Protect internal-only routes pattern: for example, any path starting with /internal
server.addHook("onRequest", async (request, reply) => {
  const url = request.raw.url || "";
  // Example: protect /internal/* routes (you can change this pattern)
  if (url.startsWith("/internal")) {
    const clientIp = String(
      request.headers["x-forwarded-for"] || request.ip || ""
    );
    if (!ipIsWhitelisted(clientIp, INTERNAL_IPS)) {
      reply.code(403).send({ error: "Forbidden" });
      return;
    }
  }
});

// --- Health endpoint
server.get("/health", async () => ({ status: "ok" }));

// --- Register routes (routes still define their own per-route configs)
server.register(milkingRoutes);
server.register(webhookRoutes); // see note below about webhook-specific config
server.register(ordersRoutes);
server.register(paymentsRoutes);
server.register(clientsRoutes);

// --- Start server
async function start() {
  try {
    const port = Number(process.env.PORT || 8080);
    await server.listen({ port, host: "0.0.0.0" });
    server.log.info(`Server listening on port ${port}`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
}

start();
