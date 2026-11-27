# ERMS frontend

Single-page app for the Emergency Resource Management System, built with React 19, TypeScript (strict), MUI 9, Vite 8 and react-router 7. It talks to the backend only through the same-origin REST API at `/api/v1` ([docs/API.md](../docs/API.md)). The architecture is in [docs/ARCHITECTURE.md §4](../docs/ARCHITECTURE.md#4-frontend).

## Prerequisites

- Node **24.15+** (`.nvmrc`) and npm 10+ (developed with Node 26 / npm 11).
- A running backend for `npm run dev`: `docker compose up backend` from the repo root, or `cd backend && ./mvnw spring-boot:run`.

## Commands

| Command                                   | What it does                                                                                                       |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `npm ci`                                  | Install the exact versions from `package-lock.json`                                                                |
| `npm run dev`                             | Vite dev server on http://localhost:5173, proxying `/api` → `$VITE_PROXY_TARGET` (default `http://localhost:8080`) |
| `npm run build`                           | Type-check (`tsc -b`) and build to `dist/`                                                                         |
| `npm run preview`                         | Serve `dist/` on :4173 with the same `/api` proxy                                                                  |
| `npm test` / `npm run test:watch`         | Vitest (jsdom + MSW; no backend needed)                                                                            |
| `npm run test:coverage`                   | Tests plus v8 coverage. Fails below lines/statements/functions 85% or branches 80%                                 |
| `npm run lint` / `npm run lint:fix`       | ESLint (typescript-eslint strict type-checked, react-hooks, jsx-a11y strict), zero warnings allowed                |
| `npm run format` / `npm run format:check` | Prettier                                                                                                           |
| `npm run typecheck`                       | `tsc -b --noEmit`                                                                                                  |

Full local gate (same as CI and `make fe-test`):

```bash
npm ci && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage && npm run build
```

To point the dev server at another backend, e.g. the Rancher Desktop ingress:

```bash
VITE_PROXY_TARGET=http://erms.localhost npm run dev
```

Demo users (backend `demo` profile) are listed in the root README and in [docs/DATA_MODEL.md](../docs/DATA_MODEL.md), e.g. `alice` / `password`.

## Project layout

```
src/
  api/          axios client (Bearer token, 401 → logout, ProblemDetail → ApiError) + one typed function per endpoint
  auth/         session storage ("erms.auth"), AuthProvider, useAuth, ProtectedRoute
  components/   AppLayout, PageHeader, ErrorAlert, Loading, EmptyState, ConfirmDialog, notifications
  features/     login, menu, incidents, resources (add + search), requests (status), reports, errors (404)
  forms/        react-hook-form adapters (pure validator → resolver, server field errors → fields)
  hooks/        useApiData (loading / error / reload with abort)
  types/api.ts  TypeScript mirror of docs/API.md
  validation/   ported legacy validation rules as pure, unit-tested functions
  test/         Vitest setup, render helpers, fixtures, MSW handlers
nginx/          nginx.conf + default.conf.template for the production image
```

Conventions:

- API calls go only through `src/api/*`. Never call axios directly from components, and never hardcode a host. The base URL is the relative `/api/v1`, and ESLint rejects `http://localhost` literals.
- No `console.log` (an ESLint error).
- Keep `src/types/api.ts` in sync with `docs/API.md` whenever the contract changes.
- Every page handles loading, empty and error states. Use `ErrorAlert` for ProblemDetail errors and `applyServerFieldErrors` for `errors[]`.

## Production image

```bash
docker build -t erms-frontend:dev .
docker run --rm -p 3000:8080 --read-only --tmpfs /tmp \
  -e BACKEND_URL=http://host.docker.internal:8080 erms-frontend:dev
```

| Property          | Value                                                                                                                                                                          |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Base              | `nginxinc/nginx-unprivileged:1.29-alpine` (build stage `node:24-alpine`)                                                                                                       |
| User              | `10101:10101` (numeric, non-root)                                                                                                                                              |
| Port              | `8080`                                                                                                                                                                         |
| Health            | `GET /healthz` → `200 ok` (Docker `HEALTHCHECK` uses it)                                                                                                                       |
| Env `BACKEND_URL` | Upstream for `/api/` (default `http://backend:8080`, no trailing slash). The host must resolve when the container starts, or nginx exits with `host not found in upstream`     |
| Writable paths    | Only `/tmp` (pid, temp files, rendered `/tmp/default.conf`). Extra tmpfs/emptyDirs at `/var/cache/nginx` and `/etc/nginx/conf.d` are allowed but not needed                    |
| Headers           | CSP (`default-src 'self'`, inline styles allowed for emotion), `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `server_tokens off` |
| Caching           | `/assets/*` immutable for 1 year; `index.html` and SPA routes `no-cache`                                                                                                       |
| Logs              | JSON access log on stdout with `request_id` (forwarded to the backend as `X-Request-Id`)                                                                                       |

A hardened run, matching Kubernetes:

```bash
docker run --rm -p 3000:8080 --user 10101:10101 --read-only \
  --tmpfs /tmp --tmpfs /var/cache/nginx --tmpfs /etc/nginx/conf.d \
  --cap-drop ALL --security-opt no-new-privileges \
  --add-host backend:host-gateway erms-frontend:dev
curl -i http://localhost:3000/healthz
curl -sI http://localhost:3000/resource-status   # deep link → index.html + security headers
```

On Kubernetes the Ingress routes `/api` to the backend directly. The nginx `/api/` proxy is used by docker-compose (and works as a fallback anywhere `BACKEND_URL` resolves).
