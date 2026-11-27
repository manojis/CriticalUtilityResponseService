# Work Packages

Three developer agents work in parallel on branch `modernize/industry-standard`, then hand off to QA. **Developers do not commit or push.** The lead integrates and commits. Stay inside your owned paths. If you need a change outside them, write it under "Handoff notes" in your final report.

`docs/ARCHITECTURE.md` (referenced below as §n), `docs/API.md` and `docs/DATA_MODEL.md` are the contract. Index of all docs: `docs/README.md`.

**Documentation ownership (keep accurate in the same change as the code):**
| Doc | Owner |
|---|---|
| `docs/API.md`, `docs/DATA_MODEL.md`, business rules in `docs/FEATURES.md`, backend sections of `docs/TESTING.md`, `backend/README.md` | WP-BE |
| ARCHITECTURE.md §4 (frontend), "UI notes" in `docs/FEATURES.md`, frontend sections of `docs/TESTING.md`, `frontend/README.md` | WP-FE |
| `docs/DEPLOYMENT.md`, `docs/RUNBOOK.md`, `docs/CICD.md` (replace every PLAT placeholder marker with exact, tested commands), `docs/SECURITY.md` secrets/hardening sections, root `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md` | WP-PLAT |
| ARCHITECTURE.md (other sections), FLOWS.md, ADRs, this file | Architect / lead |

If an implementation deviates from a doc, update the doc and list the deviation in your report. Don't edit docs owned by another package; put the change in your handoff notes.

**Dockerfile ownership:** each component developer owns their component's `Dockerfile`, `.dockerignore` and (for the frontend) the nginx config, because they can build-verify it. Platform consumes the image contracts in §3.11 and §4.5 and must not edit those files.

Environment notes (developer Mac): Java 21 is installed. Maven is **not** installed (use the wrapper; regenerate it with `docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -N wrapper:wrapper -Dmaven=3.9.11` or the latest 3.9.x). Node 26 and npm 11 are installed. Docker is Rancher Desktop moby (`unix://$HOME/.rd/docker.sock`). Kubernetes was just enabled in Rancher Desktop (context `rancher-desktop`), and it may still be starting.

---

## WP-BE — Backend developer
**Owns:** everything under `backend/` (after `git mv erms backend`), including `backend/Dockerfile`, `backend/.dockerignore`, `backend/README.md`, the Maven wrapper files, and the Flyway SQL.

**Tasks**
1. `git mv erms backend`. Delete legacy classes and files per §3.2. Regenerate a working Maven wrapper (`.mvn/wrapper/maven-wrapper.properties` must exist).
2. `pom.xml`: Boot 4.1.3 parent, Java 21, the dependencies in §3.1, Spotless, Surefire + Failsafe, JaCoCo (merge + check rules from §3.10), and `groupId edu.gatech`, `artifactId erms-backend`, `finalName app`.
3. Implement packages, config, security, errors and the API exactly per §3.2–§3.8.
4. Flyway `V1__schema.sql`, `V2__reference_data.sql` and `db/seed/R__demo_seed.sql` per §3.9.
5. Tests per §3.10, including a regression test for every **T** item in §6.
6. Dockerfile per §3.11, and `backend/README.md` (run, test, env vars, Testcontainers on Rancher Desktop).
7. Keep `docs/API.md` and `docs/DATA_MODEL.md` exactly in sync with the code (endpoints, fields, status codes, schema).

**Acceptance criteria**
- `cd backend && ./mvnw -B verify` passes locally, with Testcontainers running against Rancher Desktop docker.
- `docker build -t erms-backend:dev backend` succeeds. The container starts as uid 10001 with `--read-only --tmpfs /tmp` against a MySQL container. `/actuator/health/readiness` on 8081 returns UP.
- `grep -rn "gatech123\|System.out\|printStackTrace" backend/src` returns nothing.
- OpenAPI at `/api/openapi` matches API.md (paths, field names, status codes).

---

## WP-FE — Frontend developer
**Owns:** everything under `frontend/` (after `git mv front-end frontend`), including `frontend/Dockerfile`, `frontend/.dockerignore`, `frontend/nginx/`, and `frontend/README.md`.

**Tasks**
1. `git mv front-end frontend`. Remove CRA, `yarn.lock`, `styled-components` and all `@material-ui/*`. Scaffold Vite 8 + React 19 + TS strict per §4.1–§4.2. Commit `package-lock.json`, created with `npm install`.
2. Port every screen per §4.3 against the API.md contract (types in `src/types/api.ts`). Port the validators into pure TS functions.
3. Auth context, protected routes, API client with interceptors, ProblemDetail error display.
4. ESLint flat config, Prettier, Vitest + RTL + MSW tests per §4.4, with a coverage threshold.
5. Dockerfile and nginx template per §4.5, and `frontend/README.md`.
6. Keep ARCHITECTURE.md §4 and the "UI notes" in `docs/FEATURES.md` accurate for what was built.

**Acceptance criteria**
- `npm ci && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage && npm run build` all pass.
- `docker build -t erms-frontend:dev frontend` succeeds. The container runs as uid 101 with `--read-only --tmpfs /tmp --tmpfs /var/cache/nginx --tmpfs /etc/nginx/conf.d`. `/healthz` returns 200. Deep links (e.g. `/resource-status`) serve index.html. The security headers are present.
- No hardcoded `localhost` URLs in `src/`, and no `console.log`.

---

## WP-PLAT — Platform developer
**Owns:** `deploy/**`, `.github/**`, `scripts/**`, `docker-compose.yml`, `Makefile`, root `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, root `.gitignore`, `.editorconfig`, and `.DS_Store` removal across the repo (`git rm --cached`, plus deleting the files).

**Tasks**
1. Helm chart per §5.2, with `values.yaml` and `values-local.yaml`.
2. `ci.yml`, `cd.yml` and `codeql.yml` per §5.3–§5.4, plus `dependabot.yml`.
3. `scripts/setup-local-runner.sh` per §5.5. `scripts/smoke-test.sh` and `scripts/deploy-local.sh` per §5.4.
4. `docker-compose.yml` per §5.1, the Makefile per §5.6, and a root README (architecture summary, quick start for compose and Rancher Desktop, CI/CD flow, runner setup, demo credentials, links to docs).
5. `CONTRIBUTING.md`: prerequisites, branch naming (`feat/…`, `fix/…`), Conventional Commits, PR checklist (tests, docs updated, no secrets), local commands, code style (Spotless/ESLint/Prettier), ADR process, security reporting pointer. `CHANGELOG.md`: Keep a Changelog format, an `[Unreleased]` section summarising this modernization (Added/Changed/Removed/Fixed/Security, referencing the §6 defect IDs).
6. Fill in every PLAT placeholder marker in `docs/DEPLOYMENT.md`, `docs/RUNBOOK.md`, `docs/CICD.md` and `docs/TESTING.md` with commands you have actually run (or that are validated by lint), and reconcile the Helm values table with the real `values.yaml`.
7. Write the scripts so they can run once the backend and frontend images exist. Until then, validate the chart with `helm lint`/`helm template` + kubeconform, and validate the workflows with `actionlint` (run it via `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:latest`) and shellcheck (`koalaman/shellcheck`).

**Do not** register the runner or deploy. The lead does that after integration.

**Acceptance criteria**
- `helm lint deploy/helm/erms -f deploy/helm/erms/values-local.yaml` passes, and `helm template … | kubeconform -strict -summary` reports 0 invalid.
- actionlint and shellcheck are clean. No `pull_request` trigger appears in any workflow whose jobs use `self-hosted`.
- No PLAT placeholder markers remain in docs. Every command in the docs matches the Makefile, scripts and chart.
- The rendered manifests meet the security requirements in §5.2: runAsNonRoot, drop ALL, readOnlyRootFilesystem (backend/frontend), probes, resources, and NetworkPolicies.

---

## Handoff protocol
1. Each developer's final report lists: files changed, commands run with pass/fail output, deviations from ARCHITECTURE.md (with reasons), and handoff notes for other packages.
2. The lead integrates, runs `docker compose up --build`, and passes to QA.
3. QA reports findings as `[BLOCKER|MAJOR|MINOR] <ID> <file:line> <what> <expected>`. The lead routes them back to the owning developer. The loop repeats until there are no BLOCKER or MAJOR findings.

---

## QA checklist
Run everything from a clean checkout of the branch. Record the evidence (command plus trimmed output).

**Build & static**
- [ ] `backend: ./mvnw -B verify` is green. JaCoCo thresholds are met (quote the numbers).
- [ ] `frontend: npm ci && lint && format:check && typecheck && test:coverage && build` is green (quote the coverage).
- [ ] helm lint + kubeconform clean; actionlint + shellcheck clean; hadolint on both Dockerfiles clean.
- [ ] Trivy fs: no CRITICAL. Trivy image on both local images: no fixable CRITICAL.
- [ ] Repo grep: no `gatech123`, `localhost:8090`, `System.out`, `printStackTrace`, `console.log`, `.DS_Store`, or committed secrets (apart from clearly labelled dev placeholders in compose).

**Contract (against running compose stack or cluster)**
- [ ] Every row of API.md: happy path + each listed error status, body shape (camelCase, ISO dates, ProblemDetail with `code` and `requestId`).
- [ ] No endpoint accepts a username param; `/api/v1/me` without token → 401 ProblemDetail.
- [ ] Login with a wrong password and with an unknown user give an identical 401 response body.
- [ ] `/api/openapi` matches API.md.

**Business rules & legacy regressions (§6)**
- [ ] F1–F6: search by keyword (name/model/capability, no duplicates, resource without capabilities found), ESF (primary vs additional), combined filters, distance with an incident (co-located = 0.0, null max distance included, ordering by distance).
- [ ] F7: create an incident; lat/lon round-trip unchanged.
- [ ] F8: 10 concurrent incident creates for MD → 10 distinct consecutive ids.
- [ ] F10/F11: report shows all 15 ESFs; totals correct after a deploy and a return.
- [ ] State machine: request → deploy → return; request → reject; request → cancel. Illegal transitions → 409. Deploying a resource already deployed elsewhere → 409. Re-requesting after reject/return is allowed. Return touches only the target request (F12). Rejected requests disappear from received (F17).
- [ ] Authorization: requester can't deploy/reject (403); a stranger can't see or act on a request (404); a stranger's incident in search → 404.
- [ ] R3: request/deploy dates equal today (not the server start date).

**Frontend (manual run via compose at http://localhost:3000 and via http://erms.localhost)**
- [ ] Login/logout; an expired or invalid token redirects to login; protected routes redirect.
- [ ] Each page in §4.3 works end-to-end, including Request vs Deploy logic, status page actions, and the report.
- [ ] Validation messages show; server field errors map onto fields; loading/empty/error states.
- [ ] Response headers include the CSP and the other security headers; deep-link refresh works.

**Containers & Kubernetes**
- [ ] Images run as non-root with read-only rootfs; healthchecks pass.
- [ ] `helm upgrade --install` on `rancher-desktop` succeeds with `--rollback-on-failure --wait` (Helm 4); `helm test` passes; `scripts/smoke-test.sh http://erms.localhost` passes.
- [ ] A second `helm upgrade` keeps the same secrets (login still works, DB data persists). Pod restart → data persists (PVC).
- [ ] NetworkPolicy: a throwaway pod in `erms` without the backend label can't reach mysql:3306.
- [ ] Management port 8081 is not reachable via `http://erms.localhost`.

**Documentation**
- [ ] `docs/API.md` matches the implemented endpoints, fields and status codes (compare with `/api/openapi` and the tests).
- [ ] `docs/DATA_MODEL.md` matches the Flyway migrations (tables, columns, constraints, reference data).
- [ ] `docs/FEATURES.md` ACs are met by the running app; UI notes match the UI.
- [ ] `docs/DEPLOYMENT.md`, `RUNBOOK.md` and `CICD.md` have no PLAT placeholder markers left; commands run as written (spot-check at least status, logs, rollback, uninstall).
- [ ] Mermaid diagrams render (GitHub preview or `npx -y @mermaid-js/mermaid-cli`); internal links resolve.
- [ ] Root README quick start works from a clean clone; `CONTRIBUTING.md` and `CHANGELOG.md` exist and are accurate.

**CI/CD**
- [ ] `ci.yml` triggers/permissions/jobs per §5.3; images job pushes only on main push; multi-arch.
- [ ] `cd.yml` cannot be triggered by `pull_request` or fork runs; uses environment `local`, concurrency, explicit kube context, `--rollback-on-failure`, smoke test, failure diagnostics.
- [ ] `setup-local-runner.sh` is idempotent, verifies checksum, applies public-repo hardening, supports `--uninstall`.
