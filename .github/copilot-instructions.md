# Copilot instructions for ERMS

ERMS (Emergency Resource Management System) is a monorepo: Spring Boot API in `backend/`, Vite/React/TypeScript SPA in `frontend/`, Helm chart in `deploy/helm/erms/`, CI/CD in `.github/workflows/`, and ops scripts in `scripts/`. `make help` lists every developer entry point.

## Source-of-truth docs

`docs/ARCHITECTURE.md`, `docs/API.md` and `docs/DATA_MODEL.md` are the contract. If code and docs disagree, fix the code; if the doc is wrong, update it **in the same change**. Any API change updates `docs/API.md` and the TS mirror `frontend/src/types/api.ts`. Chart, workflow or script changes update `docs/DEPLOYMENT.md`, `docs/CICD.md` and/or `docs/RUNBOOK.md`. Significant decisions get an ADR in `docs/adr/` (next number, `Status: Proposed`, add to `docs/README.md` index). User-visible changes go in `CHANGELOG.md` under `[Unreleased]`. (`backend/README.md` is stale legacy text; ignore it.)

## Build, test, lint

Backend (Java 21, always use the wrapper `./mvnw`; integration tests need Docker for Testcontainers):

```bash
make be-test     # cd backend && ./mvnw -B -ntp verify  → Spotless check + unit + ITs + JaCoCo gates
make be-format   # ./mvnw spotless:apply (google-java-format) — run before committing Java
cd backend && ./mvnw -B test                              # unit + @WebMvcTest slices only (fast, no Docker)
cd backend && ./mvnw -B test -Dtest=ResourceServiceTest   # single unit/slice test class (or -Dtest='Class#method')
# single integration test (*IT, Failsafe); skip JaCoCo since partial runs fail the coverage gate:
cd backend && ./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ResourceIT -Djacoco.skip=true
```

On Rancher Desktop, raw `./mvnw` runs of ITs need `DOCKER_HOST=unix://$HOME/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` (`make be-test` sets these automatically).

Frontend (Node 24, `npm ci` with the committed lockfile; no backend needed for tests — MSW mocks the API):

```bash
make fe-test     # npm run lint && format:check && typecheck && test:coverage
make fe-format   # prettier --write
cd frontend && npx vitest run src/features/login/LoginPage.test.tsx   # single file
cd frontend && npx vitest run -t "name of test"                       # by test name
npm run dev      # Vite on :5173, proxies /api → VITE_PROXY_TARGET (default http://localhost:8080)
```

Platform: `make lint` runs everything (Spotless, ESLint/Prettier/tsc, `helm lint --strict` + kubeconform, shellcheck, actionlint, hadolint — the latter tools via pinned Docker images). `make up` / `make down` runs the full stack via docker compose (app at http://localhost:3000, Swagger at http://localhost:8080/api/docs). `make deploy-local` deploys to Rancher Desktop k8s; `make smoke BASE_URL=…` runs `scripts/smoke-test.sh`. Demo users (profile `demo`): `alice`, `metro_city`, `fema_atl`, `acme_rescue`, password `password`.

Coverage gates fail the build: backend JaCoCo (unit + IT merged) ≥ 80% lines on `service` and `repository`, ≥ 70% overall; frontend Vitest v8 lines/statements/functions ≥ 85%, branches ≥ 80%.

## Backend architecture (`edu.gatech.erms`)

- Layering: `api` (controllers + `api/dto` request/response records with Bean Validation) → `service` (business rules, **`@Transactional` boundaries live here**, `*Command` records as inputs) → `repository` (`NamedParameterJdbcTemplate`, **no JPA**; SQL as `private static final` text blocks) → `domain` (immutable records/enums). Controllers map DTOs ↔ domain; paths use `ApiPaths.V1` (`/api/v1`).
- Errors: throw subclasses of `exception/ErmsException` (`NotFoundException`, `ConflictException`, `ForbiddenException`, `FieldValidationException`, `InvalidCredentialsException`); each defines `status()` and a `code()` from `ErrorCodes`. `GlobalExceptionHandler` renders RFC 9457 ProblemDetail with `code`, `requestId` and sorted `errors[]`. Exception messages become the client-visible `detail`, so keep them safe; never echo internal exception text (500s are generic).
- Security: stateless HS256 JWT (Spring OAuth2 resource server). Caller identity **always** comes from the JWT `sub` via `CurrentUser`; no endpoint accepts a username from the client. Entities not visible to the caller return **404, not 403** (incidents are owner-only; resource requests are visible only to requester or resource owner).
- Request state machine (`ResourceRequestService`): `PENDING → DEPLOYED | REJECTED | CANCELLED`, `DEPLOYED → RETURNED`. Transitions use `UPDATE … WHERE request_id = :id AND status = :expected` and check the affected row count (0 → 409). DB unique keys (`uq_request_active_pair`, `uq_request_one_deployment`) also guard races; `DuplicateKeyException` maps to 409. Rows are never deleted.
- Availability is **derived**: a resource is unavailable iff a `DEPLOYED` request exists; there is no availability column.
- Time: every "today" comes from the injected `Clock` bean (`ERMS_TIME_ZONE`, default UTC). Use `LocalDate`/`Instant`, never `java.util.Date`/`java.sql.Date` in APIs.
- Config is env-var driven (`ErmsProperties`, prefix `erms`); no secrets in code or YAML. Profiles: `local`, `demo` (adds `db/seed/R__demo_seed.sql`), `test`.
- Flyway migrations in `src/main/resources/db/migration`: **never edit an applied migration**; add a new `V{n}__*.sql`, keep changes backward-compatible (expand/contract). Tables are snake_case, `utf8mb4_0900_ai_ci` (so `LIKE` is case-insensitive). Escape user LIKE input with `JdbcSupport.containsPattern`.
- Actuator runs on management port 8081 (cluster-internal, not exposed via ingress); app on 8080.
- Style: constructor injection only, no mutable bean state, SLF4J only (no `System.out`/`printStackTrace`), `org.jspecify` `@Nullable` for nullable values.

## Backend tests

- `*Test` → Surefire (unit with Mockito, and web slices); `*IT` → Failsafe (Testcontainers MySQL 8.4, in `src/test/java/.../it/`).
- Web slice tests use the `@ErmsWebMvcTest(SomeController.class)` meta-annotation (real `SecurityConfig`, `test` profile) rather than bare `@WebMvcTest`.
- ITs extend `support/AbstractIntegrationTest`, which wipes all non-reference tables before each test and provides helpers (`createUser`, `bearer`, `as`, `postJson`, `createIncident`, `createResource`, `requestResource`, `action`). ITs create their own data — never depend on the demo seed. Use `support/MutableClock` to control dates.
- Regression tests for legacy defects (IDs in `docs/ARCHITECTURE.md` §6) are named after the defect, e.g. `keywordSearch_findsResourceWithoutCapabilities_F1`.

## Frontend architecture (`frontend/src`)

- Shared layers (`api`, `auth`, `components`, `forms`, `hooks`, `validation`, `utils`, `types`) plus one folder per feature in `features/` holding the page, its page-specific components and colocated `*.test.tsx`.
- `api/client.ts` is the single axios instance (relative `baseURL` `/api/v1`, adds the Bearer token, routes any non-login 401 to the auth handler, normalises every error to `ApiError`). Each endpoint is one typed function in `api/*.ts`, documented with its API.md number (`#12`, …). Don't call axios directly from components.
- Auth session lives in `sessionStorage` key `erms.auth` (`auth/session.ts`); `AuthProvider` handles expiry and redirects to `/login?expired=1`.
- Data loading via `hooks/useApiData` (abortable, takes an `AbortSignal`). Forms use react-hook-form with `forms/formUtils.validatorResolver` wrapping pure validators from `validation/`, and `applyServerFieldErrors` to map ProblemDetail `errors[]` onto fields.
- API responses **omit null fields**: type nullable fields as optional (`field?: T`) and test with `== null`/truthiness, not `=== null`.
- Lint rules enforced as errors: no `console.*`, no hardcoded `http://localhost` in `src/`, jsx-a11y `strict`, typescript-eslint `strictTypeChecked`; TS is strict with `noUncheckedIndexedAccess`. Dependencies are pinned exactly (`.npmrc` `save-exact=true`).
- Tests: Vitest + RTL + MSW. Render pages through the real route table using helpers in `src/test/render.tsx` (`renderWithProviders`, `signIn`, `currentLocation`); add/override API handlers in `src/test/msw/handlers.ts` / via `server.use(...)`. MSW uses `onUnhandledRequest: 'error'`, so every request a test triggers needs a handler. Shared fixtures are in `src/test/fixtures.ts`.

## Helm chart (`deploy/helm/erms`)

- `values.yaml` holds production-leaning defaults; `values-local.yaml` holds Rancher Desktop overrides (`backend.profiles: demo`, `apiDocsEnabled: true`, `storageClass: local-path`). Every value is documented with a `# --` comment and in the values table in `docs/DEPLOYMENT.md`.
- `values.schema.json` uses `additionalProperties: false` throughout: **a new value must be added to the schema too**, or `helm lint --strict` fails. Image tags accept integers because `--set` parses all-digit SHAs as numbers.
- Use the `_helpers.tpl` helpers rather than hand-writing names, labels or images: `erms.{backend,frontend,mysql}.fullname`, `erms.labels`/`erms.selectorLabels` (standard `app.kubernetes.io/*` labels plus `component`), `erms.appImage`/`erms.image`, `erms.containerSecurityContext`, `erms.secretName`, `erms.datasourceUrl`.
- Every workload must stay compatible with the `restricted` Pod Security level: `runAsNonRoot`, `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `readOnlyRootFilesystem: true` with emptyDirs for writable paths. UIDs: backend 10001, frontend 101, mysql 999.
- Secret `erms-secrets` (`mysql-root-password`, `mysql-password`, `jwt-secret`) is generated in-cluster with `lookup` so values survive upgrades, and is annotated `helm.sh/resource-policy: keep`. It is skipped when `secrets.existingSecret` is set. Never put real secrets in values files.
- Backend probes and the Prometheus annotations use the management port 8081, which is never routed by the Ingress (only `/api` → backend:8080 and `/` → frontend:8080). The backend pod has a `checksum/config` annotation, so ConfigMap changes roll the pods.
- NetworkPolicies restrict traffic: MySQL only from the backend; the backend only from the ingress namespace, frontend pods and pods labelled `app.kubernetes.io/component: test`. A new pod that must reach a service needs a matching policy rule. The `helm test` pod (`templates/tests/test-health.yaml`) relies on the `test` label.
- Always pass the kube context explicitly (`--kube-context rancher-desktop` / `kubectl --context rancher-desktop`, namespace `erms`, release `erms`); never run `kubectl config use-context`. Helm 4 uses `--rollback-on-failure` and Helm 3 uses `--atomic`; scripts pick the flag from `helm version`.
- Preview with `make helm-template`; validate with `make helm-lint` (both values files + kubeconform `-strict` for Kubernetes 1.36.0).

## CI/CD and self-hosted runner

- CI (`ci.yml`, GitHub-hosted runners) runs the jobs `backend`, `frontend`, `helm`, `dockerfiles`, `infra-lint` (actionlint, shellcheck, self-hosted guard), `security` (Trivy) and `images`. The single required check is the aggregate job **`CI result`**, plus the CodeQL `analyze (…)` checks. Add new jobs to `CI result`'s `needs`. Images go to `ghcr.io/manojis/erms-{backend,frontend}:<sha>` (and `:latest`) only on push to `main`, built for amd64 and arm64.
- CD (`cd.yml`) runs on the self-hosted Mac runner (`[self-hosted, macOS, ARM64, rancher-desktop]`, environment `local`, `main` only). It is triggered by a successful CI `push` run on this repo (`workflow_run`) or by `workflow_dispatch` with `image_tag`. It checks out CI's `head_sha`, pulls the images by SHA into Rancher Desktop's moby store (shared with k3s, so there is no pull secret), runs `helm upgrade --install … --rollback-on-failure --wait`, `helm test`, then `scripts/smoke-test.sh`, and runs `scripts/diagnostics.sh` on failure. CD never deploys `:latest`.
- **The repo is public:** `cd.yml` is the only workflow that may use the self-hosted runner, and no workflow with a `pull_request`/`pull_request_target` trigger may use `runs-on: self-hosted`. The `infra-lint` guard fails CI if one does. Workflow inputs (e.g. `image_tag`) are validated and passed via `env:`, never interpolated into `run:` scripts. Checkouts use `persist-credentials: false`, top-level `permissions: contents: read`, with wider permissions granted per job only. The CD job uses a job-scoped `DOCKER_CONFIG` under `$RUNNER_TEMP` and always runs `docker logout`.
- Pin every third-party action to a full commit SHA with a `# vX.Y.Z` comment (Dependabot updates them); workflows must be actionlint-clean. No repository secrets are used; GHCR access uses `GITHUB_TOKEN`.
- The runner is installed by `scripts/setup-local-runner.sh` (`make runner-setup`; it is idempotent, so re-run it to repair). It runs as the hidden unprivileged macOS user `_ermsrunner` from the LaunchDaemon `dev.erms.actions-runner` (`/opt/erms-runner`), with root-owned `kubectl`/`helm` in `/opt/erms-runner/bin`, **no docker socket** (k3s pulls the public GHCR images) and a kubeconfig for the ServiceAccount `erms/erms-deployer` (`deploy/runner/deployer-rbac.yaml`, namespace `erms` only). CD steps must therefore not use docker, cluster-scoped resources or other namespaces; extend the Role if the chart gains a new kind. `make runner-start`/`runner-stop`/`runner-status` control it (sudo). The script also applies the repo hardening (environment `local` limited to `main` with the owner as required reviewer, approval required for fork PRs from external contributors, read-only default `GITHUB_TOKEN`). Runner troubleshooting is in `docs/CICD.md` and `docs/RUNBOOK.md`.
- `scripts/agent-loop.sh` (`make agent-loop TASK="…"`) runs an unattended build loop on a new `agent/…` branch in a separate worktree. It starts from a snapshot of the working tree, or from `--base <ref>`. Each round has three steps:
  1. An implementer agent (`claude-opus-5.5`) designs and implements the change with unit and functional tests.
  2. The script runs `make be-test fe-test` itself.
  3. A non-Claude tester agent (`gpt-6.2-sol`) does independent QA. It may only add or change test files, and writes `VERDICT: PASS|FAIL`.

  Feedback goes back to the implementer until the tests pass and the tester says PASS. Agents may not commit, push, deploy or switch branches; the script does the committing. Run artifacts are in `~/.cache/erms-agent-loop/<run-id>/`. See `scripts/agent-loop.sh --help`.
- `scripts/deploy-local.sh` (`make deploy-local`) runs the same deploy without CI, using locally built `local-<short-sha>` images. Shell scripts use `set -euo pipefail` and must be shellcheck-clean.
- Both images run as non-root with a read-only root filesystem (only `/tmp` must be writable); Dockerfiles must be hadolint-clean at `warning`. Only the labelled `change-me-…` placeholders in `docker-compose.yml` may be committed as secrets.

## Workflow

Branch from `main` (`feat/…`, `fix/…`, `docs/…`, `ci/…`, `chore/…`, `refactor/…`, `test/…`), use Conventional Commits (`feat(api): …`, `fix(frontend): …`; `!` + `BREAKING CHANGE:` footer for breaking changes), open a PR against `main` using `.github/pull_request_template.md`, squash-merge. `main` is protected by rulesets: every change needs a PR from an allowed branch prefix (also `dependabot/…`, `agent/…`; enforced by the required `Source branch` check in `branch-policy.yml`), squash-merge only, no direct or force pushes, and only `@manojis` can approve (sole code owner) and merge (restrict-updates rule bypassed only by the repository admin, only through a PR; `gh pr merge <n> --squash --admin`).
