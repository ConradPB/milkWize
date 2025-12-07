import Fastify from "fastify";
import helmet from "@fastify/helmet";
import formbody from "@fastify/formbody";
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

// Sync plugin registrations (no await required)
server.register(helmet);
server.register(formbody);

// Health endpoint (keep this available immediately)
server.get("/health", async () => ({ status: "ok" }));

// Helper: check if ip is whitelisted
const INTERNAL_IPS = (process.env.INTERNAL_IPS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
function ipIsWhitelisted(ip: string | undefined, whitelist: string[]) {
  if (!ip) return false;
  if (whitelist.includes(ip)) return true;
  const maybe = String(ip)
    .split(",")
    .map((s) => s.trim())[0];
  return whitelist.includes(maybe);
}

// Protect internal routes (sync hook is fine)
server.addHook("onRequest", async (request, reply) => {
  const url = request.raw.url || "";
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

async function start() {
  try {
    // --- CORS (async register)
    const CORS_ORIGIN = process.env.CORS_ORIGIN || "";
    await server.register(cors, {
      origin: (origin, cb) => {
        if (!origin) return cb(null, true);
        if (!CORS_ORIGIN || CORS_ORIGIN === "*") return cb(null, true);
        const allowed = CORS_ORIGIN.split(",").map((s) => s.trim());
        if (allowed.includes(origin)) return cb(null, true);
        return new Error("Not allowed by CORS");
      },
      methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
      allowedHeaders: ["Content-Type", "Authorization", "x-webhook-signature"],
      credentials: true,
    });

    // --- Rate limiting (async register)
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

    server.register(milkingRoutes);
    server.register(webhookRoutes);
    server.register(ordersRoutes);
    server.register(paymentsRoutes);
    server.register(clientsRoutes);

    // Start listening
    const port = Number(process.env.PORT || 8080);
    await server.listen({ port, host: "0.0.0.0" });

    server.log.info(`Server listening on port ${port}`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
}

start();
