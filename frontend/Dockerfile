# Build depuis la racine du dépôt :
#   docker compose build frontend
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
RUN apk add --no-cache wget
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=8 \
    CMD wget -qO- http://127.0.0.1/healthz || exit 1
