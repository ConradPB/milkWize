# Use official Node 20 LTS
FROM node:20-bullseye-slim AS builder

# Install pnpm
RUN npm install -g pnpm@8

WORKDIR /app

# Copy package manifests first to cache installs
COPY package.json pnpm-lock.yaml* ./

# Install deps
RUN pnpm install --frozen-lockfile

# Copy source
COPY . .

# Build (assumes you have a build script e.g. tsc -> dist)
# If you use tsc: ensure "build" compiles into dist/
RUN pnpm run build

# ---- production image ----
FROM node:20-bullseye-slim AS runtime
RUN npm install -g pnpm@8
WORKDIR /app

# Drop to non-root user for security
RUN useradd --user-group --create-home --shell /bin/false appuser
USER appuser

# Copy only what's needed from builder
COPY --from=builder /app/package.json /app/pnpm-lock.yaml* /app/
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist

# Environment
ENV NODE_ENV=production
ENV PORT=8080

EXPOSE 8080

# Start command (adjust if your compiled entrypoint differs)
CMD ["node", "dist/index.js"]
