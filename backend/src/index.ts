import Fastify from "fastify";
import helmet from "fastify-helmet";
import formbody from "fastify-formbody";
import dotenv from "dotenv";
import milkingRoutes from "./routes/milking";
import webhookRoutes from "./routes/webhook";

dotenv.config();
const server = Fastify({ logger: true });
server.register(helmet);
server.register(formbody);

server.get("/health", async () => ({ status: "ok" }));

server.register(milkingRoutes);
server.register(webhookRoutes);

const port = Number(process.env.PORT || 8080);
server
  .listen({ port, host: "0.0.0.0" })
  .then(() => server.log.info(`Server listening on ${port}`))
  .catch((err) => {
    server.log.error(err);
    process.exit(1);
  });
