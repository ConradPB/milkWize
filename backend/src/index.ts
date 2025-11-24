import Fastify from "fastify";
import helmet from "fastify-helmet";
import formbody from "fastify-formbody";
import dotenv from "dotenv";
import rateLimit from "@fastify/rate-limit";

import milkingRoutes from "./routes/milking";
import webhookRoutes from "./routes/webhook";
import ordersRoutes from "./routes/orders";
import paymentsRoutes from "./routes/payments";
import clientsRoutes from "./routes/clients";

dotenv.config();

const server = Fastify({ logger: true });

// Register sync plugins
server.register(helmet);
server.register(formbody);

// Health check endpoint
server.get("/health", async () => {
  return { status: "ok" };
});

// Wrap async startup
async function start() {
  try {
    // Register Rate Limiter
    await server.register(rateLimit, {
      max: Number(process.env.RATE_LIMIT_DEFAULT_MAX || 100),
      timeWindow: process.env.RATE_LIMIT_DEFAULT_WINDOW || "1 minute",
      keyGenerator: (req) => {
        return String(req.headers["x-forwarded-for"] || req.ip || "unknown");
      },
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

    // Register routes
    server.register(milkingRoutes);
    server.register(webhookRoutes);
    server.register(ordersRoutes);
    server.register(paymentsRoutes);
    server.register(clientsRoutes);

    const port = Number(process.env.PORT || 8080);
    await server.listen({ port, host: "0.0.0.0" });

    server.log.info(`Server listening on port ${port}`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
}

start();
