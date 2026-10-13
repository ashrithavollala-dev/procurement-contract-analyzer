# syntax=docker/dockerfile:1
# ---- Build stage: Node 20 + Vite production build ---------------------------------------------
FROM node:20.19.5-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The browser calls the same origin; nginx proxies /api to the backend.
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- Runtime stage: static files served by nginx (never `vite dev` in production) -------------
FROM nginx:1.27.5-alpine
ENV BACKEND_UPSTREAM=backend:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz || exit 1
