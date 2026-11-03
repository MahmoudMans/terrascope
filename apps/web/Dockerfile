FROM node:22-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json* ./

RUN --mount=type=cache,target=/root/.npm \
    if [ -f package-lock.json ]; then npm ci; else npm install; fi

FROM node:22-alpine AS build

WORKDIR /app

ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8087/api/v1

ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN mkdir -p public && npm run build

FROM node:22-alpine AS run

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -S terrascope && adduser -S terrascope -G terrascope

COPY --from=build --chown=terrascope:terrascope /app/.next/standalone ./
COPY --from=build --chown=terrascope:terrascope /app/.next/static ./.next/static
COPY --from=build --chown=terrascope:terrascope /app/public ./public

USER terrascope

EXPOSE 3000

CMD ["node", "server.js"]
