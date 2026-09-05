# syntax=docker/dockerfile:1

FROM oven/bun:1.3.10-alpine AS base

WORKDIR /app

# Install dependencies separately so source-only changes can reuse this layer.
FROM base AS dependencies

COPY package.json bun.lock turbo.json tsconfig.json ./
COPY apps/server/package.json apps/server/package.json
COPY packages/auth/package.json packages/auth/package.json
COPY packages/config/package.json packages/config/package.json
COPY packages/db/package.json packages/db/package.json
COPY packages/env/package.json packages/env/package.json

RUN bun install --frozen-lockfile

FROM dependencies AS build

COPY apps ./apps
COPY packages ./packages
COPY kubojs.jsonrc ./kubojs.jsonrc

RUN bun run build --filter=server

# Keep only production dependencies in the runtime image.
FROM base AS production

ENV NODE_ENV=production

COPY package.json bun.lock turbo.json tsconfig.json ./
COPY apps/server/package.json apps/server/package.json
COPY packages/auth/package.json packages/auth/package.json
COPY packages/config/package.json packages/config/package.json
COPY packages/db/package.json packages/db/package.json
COPY packages/env/package.json packages/env/package.json

RUN bun install --frozen-lockfile --production

COPY --from=build /app/apps/server/dist ./apps/server/dist

EXPOSE 3000

CMD ["bun", "run", "apps/server/dist/index.mjs"]
