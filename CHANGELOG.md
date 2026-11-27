# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Defect IDs (S*, R*, F*) refer to [docs/ARCHITECTURE.md §6](docs/ARCHITECTURE.md#6-legacy-defects-required-fixes-and-regression-tests).

## [Unreleased]

Modernization of the legacy proof of concept into an industry-standard, deployable system.

### Added
- REST API v1 under `/api/v1` with RFC 9457 ProblemDetail errors (`code`, `requestId`) and an OpenAPI description at `/api/openapi` (Swagger UI at `/api/docs` when enabled).
- Stateless JWT authentication (HS256) with BCrypt password hashes ([ADR-0003](docs/adr/0003-stateless-jwt-auth.md)).
- Flyway schema (`V1`), reference data (`V2`) and an idempotent demo seed for the `demo` profile ([ADR-0004](docs/adr/0004-schema-and-request-state-machine.md)).
- Server-side request state machine (PENDING → DEPLOYED → RETURNED, REJECTED, CANCELLED) with derived resource availability and DB-enforced invariants.
- New frontend: Vite + React 19 + strict TypeScript + MUI with an API client, auth context, protected routes, and Vitest/RTL/MSW tests ([ADR-0005](docs/adr/0005-frontend-vite-react-ts-mui.md)).
- Separate management port 8081 with Kubernetes probes, Prometheus metrics, and ECS JSON logs with `requestId` ([ADR-0008](docs/adr/0008-observability-and-management-port.md)).
- Container images for backend (non-root uid 10001) and frontend (nginx-unprivileged, uid 10101), both able to run with a read-only root filesystem.
- Helm chart `deploy/helm/erms`: backend and frontend Deployments, a MySQL 8.4 StatefulSet with PVC, Traefik Ingress (`erms.localhost`), NetworkPolicies, restricted pod security, a stable chart-managed Secret, optional PDB/HPA, and a `helm test` hook ([ADR-0006](docs/adr/0006-helm-chart-in-chart-mysql.md)).
- GitHub Actions: CI (backend verify, frontend lint/typecheck/test/build, helm lint + kubeconform, hadolint, actionlint, shellcheck, Trivy fs + image with SARIF, multi-arch GHCR images with SBOM and provenance), CD to Rancher Desktop through a self-hosted runner, CodeQL (Java, TypeScript, Actions) and Dependabot ([ADR-0007](docs/adr/0007-ci-github-hosted-cd-self-hosted.md)).
- Scripts: `setup-local-runner.sh` (checksum-verified runner install as the isolated, unprivileged macOS user `_ermsrunner` in a LaunchDaemon with a namespace-scoped Kubernetes ServiceAccount (`deploy/runner/deployer-rbac.yaml`) and no Docker access; `make runner-start`/`runner-stop`/`runner-status`; environment `local` with the owner as required reviewer; repository hardening), `deploy-local.sh`, `smoke-test.sh` and `diagnostics.sh`.
- `docker-compose.yml` for local development, a `Makefile`, and documentation (architecture, API, data model, security, testing, deployment, CI/CD, runbook, ADRs, contributing guide).

### Changed
- Repository layout: `erms/` → `backend/`, `front-end/` → `frontend/` ([ADR-0001](docs/adr/0001-monorepo-layout.md)).
- Java 8 / Spring Boot 2.0.2 → Java 21 / Spring Boot 4.1 with HikariCP and `NamedParameterJdbcTemplate` ([ADR-0002](docs/adr/0002-spring-boot-4-jdbc.md)) (R1, R8, R9).
- Configuration comes from environment variables and Kubernetes Secrets instead of hardcoded values (S1, S5).
- Snake_case schema with foreign keys, CHECK constraints and indexes. Money and coordinates use `DECIMAL` (F22).
- All dates are ISO `YYYY-MM-DD` `LocalDate` values, and "today" comes from an injected `Clock` (F9, R3).
- Reference data endpoints return ordered arrays (F23). The frontend uses a relative `/api` base URL (R11, F24).

### Removed
- Create React App, `react-scripts`, `yarn.lock`, `styled-components` and `@material-ui/*`.
- Legacy classes `TestingMain`, `ErmsUtil`, `SqlConstants`, `SqlConnection`, `model/*`, the old exceptions, `ErmsRestService`, `ErmsService*` and `log4j2.xml` (R8, R9).
- Client-supplied `username` parameters on every endpoint (S3), and the client-side incident-count endpoint (F8).
- All `.DS_Store` files.

### Fixed
- Connection, statement and result-set leaks, and thread-unsafe singleton state (R1, R2, R4, R5).
- Multi-step writes are now transactional (R6). Null capability or ESF lists no longer cause an NPE (R7).
- Keyword, ESF and combined search semantics (F1, F2, F3, F5), distance filtering for co-located resources and unlimited ranges (F4), and ordering by distance (F6).
- Incident latitude/longitude swap (F7). Incident IDs are generated server-side under a row lock (F8).
- The resource report query fails no more on MySQL 8 and the totals are correct (F10, F11).
- Request lifecycle defects: return touches only the target request (F12), no double deployment (F13), re-requests after reject/return (F14), reject no longer corrupts availability (F15), cancel keeps the audit trail (F16), received/in-use lists filter by status (F17, F18).
- HTTP semantics: 404 instead of 204 with a body (F19), 400 for type mismatches (F20), optional search parameters (F21), 401 for failed logins (S7).
- The only test needed a live database; it now uses Testcontainers (R10).

### Security
- BCrypt password hashing and no credential logging (S2). JWT identity only (S3). CORS allow-list, off by default (S4). Generic 500 responses (S6). LIKE wildcard escaping (S9).
- Current dependencies with Dependabot, Trivy and CodeQL (S10).
- Hardened containers and pods: non-root, read-only root filesystem, all capabilities dropped, `seccompProfile: RuntimeDefault`, no service-account token, Pod Security `restricted` namespace, NetworkPolicies, and a management port that is never exposed by the Ingress.
- Supply chain: actions pinned to commit SHAs, least-privilege workflow permissions, sha-tagged images with SBOM and provenance, CD only from `main` pushes of this repository, fork-PR approval for all external contributors, secret scanning with push protection.
- Frontend security headers (CSP, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`) (S8).
