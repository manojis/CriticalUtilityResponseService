# syntax=docker/dockerfile:1.7
#
# ERMS frontend image (contract: docs/ARCHITECTURE.md §4.5)
#   - nginx-unprivileged, uid/gid 101, listens on 8080
#   - env BACKEND_URL (default http://backend:8080) → upstream for /api/
#   - GET /healthz → 200 "ok"
#   - runs with a read-only root filesystem; only /tmp must be writable (tmpfs / emptyDir).
#     Mounting /var/cache/nginx and /etc/nginx/conf.d as well (per §4.5) is harmless.

# ---- build stage: runs natively on the build host, output is platform-independent ----------
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
ENV CI=true \
    NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false \
    NPM_CONFIG_AUDIT=false
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY index.html vite.config.ts tsconfig.json tsconfig.app.json tsconfig.node.json ./
COPY public ./public
COPY src ./src
RUN npm run build

# ---- runtime stage --------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.29-alpine

ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="erms-frontend" \
      org.opencontainers.image.description="ERMS single-page application served by nginx" \
      org.opencontainers.image.source="https://github.com/manojis/CriticalUtilityResponseService" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"

# BACKEND_URL: upstream for the /api/ reverse proxy (no trailing slash). Override per environment.
# NGINX_ENVSUBST_OUTPUT_DIR: render the server config into /tmp so the root FS can be read-only.
ENV BACKEND_URL=http://backend:8080 \
    NGINX_ENVSUBST_OUTPUT_DIR=/tmp

# Replace the stock server config; config files are root-owned and read-only for uid 101.
USER 0
RUN rm -f /etc/nginx/conf.d/default.conf
COPY --chown=root:root nginx/nginx.conf /etc/nginx/nginx.conf
COPY --chown=root:root nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build --chown=root:root /app/dist /usr/share/nginx/html

# Numeric user so Kubernetes can verify runAsNonRoot. pid file is /tmp/nginx.pid in this image.
USER 101:101
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD ["wget", "-qO-", "http://127.0.0.1:8080/healthz"]
