# ERMS — Target Architecture

Emergency Resource Management System (ERMS). Users register **resources** (equipment/people, classified by FEMA Emergency Support Function, or ESF), declare **incidents**, search for resources near an incident, and run a **request → deploy → return** workflow.

This document plus [API.md](API.md) and [DATA_MODEL.md](DATA_MODEL.md) are **the contract** for the three work packages in `WORK_PACKAGES.md`. If code and this doc disagree, fix the code. If the doc is wrong, update the doc in the same change.

---

## 1. Repository layout

```
/
├── backend/                 # was erms/   (git mv)
│   ├── Dockerfile, .dockerignore
│   ├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
│   ├── pom.xml
│   └── src/{main,test}/...
├── frontend/                # was front-end/ (git mv)
│   ├── Dockerfile, .dockerignore, nginx/{nginx.conf,default.conf.template}
│   ├── package.json, package-lock.json (yarn.lock deleted)
│   ├── index.html, vite.config.ts, vitest.config.ts, tsconfig*.json, eslint.config.js, .prettierrc.json
│   └── src/...
├── deploy/helm/erms/        # Helm chart
├── .github/workflows/{ci.yml,cd.yml,codeql.yml}, .github/dependabot.yml
├── scripts/{setup-local-runner.sh,smoke-test.sh,deploy-local.sh,diagnostics.sh}
├── docs/{ARCHITECTURE.md,WORK_PACKAGES.md,adr/}
├── docker-compose.yml, Makefile, README.md, .gitignore, .editorconfig
```
Delete every `.DS_Store` and add it to the root `.gitignore`.

## 2. Runtime topology

```
Browser ──► Traefik Ingress (host erms.localhost, :80)
             ├── /api/*  ──► svc erms-backend:8080   (Spring Boot, app port)
             └── /*      ──► svc erms-frontend:8080  (nginx-unprivileged, SPA)
erms-backend ──► svc erms-mysql:3306 (StatefulSet, PVC local-path)
erms-backend :8081 (management: actuator health/prometheus). Cluster-internal only, NOT exposed via Ingress.
```
Namespace: `erms`. Release name: `erms`. Kube context: `rancher-desktop`.

| Component | Container port | Host port (docker-compose) |
|---|---|---|
| backend app | 8080 | 8080 |
| backend management | 8081 | 8081 |
| frontend nginx | 8080 | 3000 |
| mysql | 3306 | 3306 (override with `MYSQL_HOST_PORT`) |

---

## 3. Backend

### 3.1 Stack
Java 21 (bytecode level; Java 25 runtime) · Spring Boot **4.2.0** · Maven 3.10.x via wrapper · MySQL 26.10 · HikariCP · `NamedParameterJdbcTemplate` (no JPA, see ADR-0002) · Flyway · Spring Security + `spring-boot-starter-oauth2-resource-server` (Nimbus JWT, HS256) · Bean Validation · Actuator + Micrometer Prometheus · springdoc-openapi (the version line that supports Boot 4) · JUnit 5, Mockito, AssertJ, Testcontainers (MySQL) · JaCoCo · Spotless (google-java-format).

Boot 4 uses modular starters. Expected: `spring-boot-starter-webmvc`, `spring-boot-starter-jdbc`, `spring-boot-starter-flyway` (+ `flyway-mysql`), `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `mysql-connector-j` (runtime), plus test starters (`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test` or `spring-security-test`, `spring-boot-testcontainers`, the Testcontainers MySQL + JUnit Jupiter modules). **Compile to verify artifact names. Do not guess-and-leave.** Remove the explicit `jackson-databind`/`commons-lang3` pins and let the Boot BOM manage them.

### 3.2 Packages (`edu.gatech.erms`)
```
ErmsApplication
config/        OpenApiConfig, ClockConfig (Clock bean, zone from erms.time-zone), ErmsProperties (@ConfigurationProperties "erms")
security/      SecurityConfig, JwtTokenService (issue), CurrentUser (resolve username from Authentication), RequestIdFilter
api/           AuthController, MeController, ReferenceDataController, IncidentController, ResourceController,
               ResourceRequestController, ReportController
api/dto/       Java records for every request/response in API.md (validation annotations on request records)
service/       AuthService, UserService, ReferenceDataService, IncidentService, ResourceService,
               ResourceRequestService, ReportService     (@Transactional boundaries live here)
repository/    UserRepository, ReferenceDataRepository, IncidentRepository, ResourceRepository,
               ResourceRequestRepository, ReportRepository (SQL as private static final text blocks)
domain/        UserType, RequestStatus enums; immutable domain records
exception/     NotFoundException, ConflictException, ForbiddenException, GlobalExceptionHandler
```
**Delete** `TestingMain`, `ErmsUtil`, `SqlConstants`, `SqlConnection`, all `model/*`, all old `exception/*`, `rest/ErmsRestService`, `service/ErmsService*`, `log4j2.xml`.

Rules: constructor injection only (no field `@Autowired`), no mutable state in beans, no `System.out`/`printStackTrace` (SLF4J only), no `java.sql.Date` or `java.util.Date` in APIs (use `LocalDate`/`Instant`), all "today" values come from the injected `Clock`.

### 3.3 Configuration (env vars, no secrets in code or YAML)

| Env var | Property | Default (application.yml) | Notes |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | spring.datasource.url | `jdbc:mysql://localhost:3306/erms?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` | |
| `SPRING_DATASOURCE_USERNAME` | | `erms` | |
| `SPRING_DATASOURCE_PASSWORD` | | *(none, required)* | |
| `ERMS_JWT_SECRET` | erms.jwt.secret | *(none, required, ≥ 32 bytes)* | fail fast at startup if missing or short |
| `ERMS_JWT_TTL` | erms.jwt.ttl | `PT1H` | ISO-8601 duration |
| `ERMS_JWT_ISSUER` | erms.jwt.issuer | `erms` | |
| `ERMS_CORS_ALLOWED_ORIGINS` | erms.cors.allowed-origins | *(empty = CORS disabled)* | comma-separated, e.g. `http://localhost:5173` |
| `ERMS_TIME_ZONE` | erms.time-zone | `UTC` | used by the Clock bean |
| `ERMS_API_DOCS_ENABLED` | springdoc.api-docs.enabled / swagger-ui.enabled | `false` | `true` in the `local`/`demo` profiles |
| `SPRING_PROFILES_ACTIVE` | | *(none)* | `local` (dev laptop), `demo` (adds demo seed data, used on Rancher Desktop) |
| `JAVA_TOOL_OPTIONS` | | set in Dockerfile | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` |

Other fixed settings: `server.port=8080`, `management.server.port=8081`, `management.endpoints.web.exposure.include=health,info,prometheus`, `management.endpoint.health.probes.enabled=true`, readiness group includes `db`, liveness group does not. `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=20s`, `spring.flyway.locations=classpath:db/migration` (profile `demo` adds `classpath:db/seed`), `logging.structured.format.console=ecs` (except profile `local`, which keeps human-readable logs), `server.error.include-stacktrace=never`, `springdoc.api-docs.path=/api/openapi`, `springdoc.swagger-ui.path=/api/docs` (so docs are reachable through the `/api` ingress path).

### 3.4 Security
- Stateless (`SessionCreationPolicy.STATELESS`), CSRF disabled (no cookies are used), `httpBasic`/`formLogin` disabled.
- Public: `POST /api/v1/auth/login`, `/api/openapi/**`, `/api/docs/**`, `/swagger-ui/**` (only when docs are enabled), and the whole management port (8081). Everything else under `/api/**` requires `Authorization: Bearer <jwt>`.
- JWT: HS256 with `NimbusJwtEncoder`/`NimbusJwtDecoder.withSecretKey`. Claims: `iss`, `sub`=username, `iat`, `exp`, `name`, `userType`. The decoder validates `iss` and `exp`.
- Passwords: `BCryptPasswordEncoder` (strength 10). Login failure returns **401** with a generic `detail` ("Invalid username or password") for unknown user and wrong password alike. Use a constant-time dummy hash check when the user doesn't exist.
- Identity always comes from `sub`. **No endpoint accepts a username from the client** (this fixes the legacy IDOR).
- CORS: only when `erms.cors.allowed-origins` is non-empty. Methods GET/POST/OPTIONS. Headers Authorization, Content-Type. Never `*`.
- 401/403 responses are ProblemDetail JSON (custom `AuthenticationEntryPoint`/`AccessDeniedHandler`).
- `RequestIdFilter`: reads `X-Request-Id` or generates a UUID, puts it in MDC `requestId`, and echoes it in the response header.

### 3.5 Errors and 3.6 REST API v1
The full contract (error model, auth, every endpoint, examples) lives in **[API.md](API.md)**, which is the source of truth for backend and frontend. In short: base path `/api/v1`, camelCase JSON, ISO `YYYY-MM-DD` dates, RFC 9457 ProblemDetail errors with `code` + `requestId`, and a Bearer JWT on everything except `POST /api/v1/auth/login`.

### 3.7 Request state machine
```
            create (deployImmediately=false)          create (deployImmediately=true, caller owns resource)
                     │                                                │
                     ▼                                                ▼
 cancel ◄────── PENDING ──── deploy (owner, resource free) ───► DEPLOYED ──── return (requester|owner) ───► RETURNED
 (requester)  │      │                                         
              │      └──── reject (owner) ───► REJECTED
              ▼
           CANCELLED
```
Side effects (all in one `@Transactional` service method):
- **create**: `request_date = today`, `status = PENDING` (or `DEPLOYED` with `deploy_date = today`).
- **deploy**: `status = DEPLOYED`, `deploy_date = today`. Enforced by DB unique key `uq_request_one_deployment` too (map `DuplicateKeyException` → 409).
- **return**: `status = RETURNED`, `actual_return_date = today` on **this request only**.
- **reject/cancel**: status only. Rows are never deleted (audit trail).
- Use `UPDATE … WHERE request_id = :id AND status = :expected` and check the affected row count (optimistic, race-safe). 0 rows → 409.

**Derived availability** (there are no `is_available`/`next_available` columns): a resource is `available=false` iff a `DEPLOYED` request exists for it. `nextAvailableDate` = that request's `expected_return_date`, or `null` when available.

### 3.8 Search semantics (one SQL query, all filters AND-combined)
- `keyword` (trimmed, non-blank): case-insensitive match on `resource_name LIKE` OR `model LIKE` OR `EXISTS (capability LIKE)`. Escape `%`, `_` and `\` in user input.
- `esf`: `primary_esf = :esf OR EXISTS (additional_esf.esf_number = :esf)`.
- `incidentId`: compute `ST_Distance_Sphere(POINT(r.longitude, r.latitude), POINT(i.longitude, i.latitude)) / 1000` as `distance_km`. Keep only rows where `r.max_distance_km IS NULL OR distance_km <= r.max_distance_km`. If `distanceKm` is given, also keep only `distance_km <= :distanceKm`. Sort by `distance_km ASC, resource_name ASC, resource_id ASC`.
- Without `incidentId`: sort by `resource_name ASC, resource_id ASC`.
- Pagination: `LIMIT :size OFFSET :page*size` plus a matching `COUNT(*)` query.
- Search covers all users' resources, including the caller's own.

### 3.9 Database: Flyway migrations
All tables: InnoDB, `utf8mb4`, `utf8mb4_0900_ai_ci` (case-insensitive collation, which makes `LIKE` case-insensitive). Table names are snake_case (this is a clean break from the legacy CamelCase names; there is no production data to migrate).

`backend/src/main/resources/db/migration/V1__schema.sql`:
```sql
CREATE TABLE app_user (
  username      VARCHAR(50)  NOT NULL PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  user_type     ENUM('INDIVIDUAL','MUNICIPALITY','GOVERNMENT_AGENCY','COMPANY') NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE individual (
  username  VARCHAR(50)  NOT NULL PRIMARY KEY,
  job_title VARCHAR(100) NOT NULL,
  hire_date DATE         NOT NULL,
  CONSTRAINT fk_individual_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
);
CREATE TABLE municipality (
  username              VARCHAR(50)  NOT NULL PRIMARY KEY,
  municipality_category VARCHAR(100) NOT NULL,
  CONSTRAINT fk_municipality_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
);
CREATE TABLE government_agency (
  username                 VARCHAR(50)  NOT NULL PRIMARY KEY,
  agency_name_local_office VARCHAR(150) NOT NULL,
  CONSTRAINT fk_government_agency_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
);
CREATE TABLE company (
  username      VARCHAR(50)  NOT NULL PRIMARY KEY,
  headquarters  VARCHAR(150) NOT NULL,
  num_employees INT UNSIGNED NOT NULL,
  CONSTRAINT fk_company_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
);
CREATE TABLE esf (
  esf_number  TINYINT UNSIGNED NOT NULL PRIMARY KEY,
  description VARCHAR(120)     NOT NULL,
  CONSTRAINT uq_esf_description UNIQUE (description)
);
CREATE TABLE cost_unit (
  unit VARCHAR(20) NOT NULL PRIMARY KEY
);
CREATE TABLE declaration (
  abbreviation CHAR(2)      NOT NULL PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  CONSTRAINT uq_declaration_name UNIQUE (name)
);
CREATE TABLE incident_sequence (
  declaration_abbreviation CHAR(2)      NOT NULL PRIMARY KEY,
  next_value               INT UNSIGNED NOT NULL DEFAULT 1,
  CONSTRAINT fk_incident_sequence_declaration FOREIGN KEY (declaration_abbreviation) REFERENCES declaration(abbreviation)
);
CREATE TABLE incident (
  incident_id              VARCHAR(16)   NOT NULL PRIMARY KEY,
  owner_username           VARCHAR(50)   NOT NULL,
  declaration_abbreviation CHAR(2)       NOT NULL,
  incident_date            DATE          NOT NULL,
  description              VARCHAR(500)  NOT NULL,
  latitude                 DECIMAL(9,6)  NOT NULL,
  longitude                DECIMAL(9,6)  NOT NULL,
  created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_incident_owner       FOREIGN KEY (owner_username) REFERENCES app_user(username),
  CONSTRAINT fk_incident_declaration FOREIGN KEY (declaration_abbreviation) REFERENCES declaration(abbreviation),
  CONSTRAINT ck_incident_lat CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_incident_lon CHECK (longitude BETWEEN -180 AND 180),
  INDEX ix_incident_owner (owner_username)
);
CREATE TABLE resource (
  resource_id     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_username  VARCHAR(50)      NOT NULL,
  resource_name   VARCHAR(100)     NOT NULL,
  model           VARCHAR(100)     NULL,
  primary_esf     TINYINT UNSIGNED NOT NULL,
  cost            DECIMAL(12,2)    NOT NULL,
  cost_unit       VARCHAR(20)      NOT NULL,
  latitude        DECIMAL(9,6)     NOT NULL,
  longitude       DECIMAL(9,6)     NOT NULL,
  max_distance_km DECIMAL(8,2)     NULL,
  created_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resource_owner FOREIGN KEY (owner_username) REFERENCES app_user(username),
  CONSTRAINT fk_resource_esf   FOREIGN KEY (primary_esf)    REFERENCES esf(esf_number),
  CONSTRAINT fk_resource_unit  FOREIGN KEY (cost_unit)      REFERENCES cost_unit(unit),
  CONSTRAINT ck_resource_cost  CHECK (cost >= 0),
  CONSTRAINT ck_resource_lat   CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_resource_lon   CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_resource_dist  CHECK (max_distance_km IS NULL OR max_distance_km > 0),
  INDEX ix_resource_owner (owner_username),
  INDEX ix_resource_primary_esf (primary_esf),
  INDEX ix_resource_name (resource_name)
);
CREATE TABLE resource_capability (
  resource_id BIGINT UNSIGNED NOT NULL,
  capability  VARCHAR(100)    NOT NULL,
  PRIMARY KEY (resource_id, capability),
  CONSTRAINT fk_capability_resource FOREIGN KEY (resource_id) REFERENCES resource(resource_id) ON DELETE CASCADE,
  INDEX ix_capability (capability)
);
CREATE TABLE additional_esf (
  resource_id BIGINT UNSIGNED  NOT NULL,
  esf_number  TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (resource_id, esf_number),
  CONSTRAINT fk_additional_esf_resource FOREIGN KEY (resource_id) REFERENCES resource(resource_id) ON DELETE CASCADE,
  CONSTRAINT fk_additional_esf_esf      FOREIGN KEY (esf_number)  REFERENCES esf(esf_number),
  INDEX ix_additional_esf_esf (esf_number)
);
CREATE TABLE resource_request (
  request_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  resource_id          BIGINT UNSIGNED NOT NULL,
  incident_id          VARCHAR(16)     NOT NULL,
  requested_by         VARCHAR(50)     NOT NULL,
  status               ENUM('PENDING','DEPLOYED','REJECTED','CANCELLED','RETURNED') NOT NULL DEFAULT 'PENDING',
  request_date         DATE            NOT NULL,
  expected_return_date DATE            NOT NULL,
  deploy_date          DATE            NULL,
  actual_return_date   DATE            NULL,
  created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- MySQL has no partial unique indexes: emulate with stored generated columns (NULLs never collide)
  active_pair          VARCHAR(40) GENERATED ALWAYS AS
                         (CASE WHEN status IN ('PENDING','DEPLOYED') THEN CONCAT(resource_id, ':', incident_id) END) STORED,
  deployed_resource_id BIGINT UNSIGNED GENERATED ALWAYS AS
                         (CASE WHEN status = 'DEPLOYED' THEN resource_id END) STORED,
  -- FKs on base columns of stored generated columns must not CASCADE/SET NULL
  CONSTRAINT fk_request_resource FOREIGN KEY (resource_id)  REFERENCES resource(resource_id),
  CONSTRAINT fk_request_incident FOREIGN KEY (incident_id)  REFERENCES incident(incident_id),
  CONSTRAINT fk_request_user     FOREIGN KEY (requested_by) REFERENCES app_user(username),
  CONSTRAINT ck_request_dates    CHECK (expected_return_date >= request_date),
  CONSTRAINT uq_request_active_pair     UNIQUE (active_pair),
  CONSTRAINT uq_request_one_deployment  UNIQUE (deployed_resource_id),
  INDEX ix_request_requester_status (requested_by, status),
  INDEX ix_request_resource_status  (resource_id, status)
);
```

`V2__reference_data.sql`:
- `esf` 1–15: 1 Transportation · 2 Communications · 3 Public Works and Engineering · 4 Firefighting · 5 Information and Planning · 6 Mass Care, Emergency Assistance, Temporary Housing, and Human Services · 7 Logistics · 8 Public Health and Medical Services · 9 Search and Rescue · 10 Oil and Hazardous Materials Response · 11 Agriculture and Natural Resources · 12 Energy · 13 Public Safety and Security · 14 Cross-Sector Business and Infrastructure · 15 External Affairs.
- `cost_unit`: Each, Hour, Day, Week.
- `declaration`: MD Major Disaster · ED Emergency · FM Fire Management Assistance · FD Federal Department.
- `incident_sequence`: one row per declaration with `next_value = 1`.

`backend/src/main/resources/db/seed/R__demo_seed.sql` (loaded only with profile `demo`; idempotent `INSERT … ON DUPLICATE KEY UPDATE`). Password for every demo user is `password`. BCrypt hash: `$2a$10$N0jCfYz6SQM7VbJHv3CpeO4mFBrOhFj823qkahHdDRNHH7XkvEYC2`. **Verify it with a unit test** (`encoder.matches("password", hash)`) and regenerate if it fails.

| username | name | type | subtype row |
|---|---|---|---|
| `alice` | Alice Smith | INDIVIDUAL | job_title "Emergency Coordinator", hire_date 2015-03-01 |
| `metro_city` | Metro City | MUNICIPALITY | municipality_category "City" |
| `fema_atl` | FEMA Region IV | GOVERNMENT_AGENCY | agency_name_local_office "FEMA Region IV - Atlanta" |
| `acme_rescue` | Acme Rescue Co | COMPANY | headquarters "Atlanta, GA", num_employees 250 |

Also seed about 6 resources across owners and ESFs (fixed `resource_id`s 1001+, with capabilities and additional ESFs), plus one incident `MD-1` owned by `alice` near Atlanta (33.749, −84.388). If you seed it, set `incident_sequence.next_value` for MD to 2 using `GREATEST`.

**Incident ID generation** (`IncidentService.create`, `@Transactional`): `SELECT next_value FROM incident_sequence WHERE declaration_abbreviation = :abbr FOR UPDATE` → `id = abbr + "-" + next_value` → `UPDATE incident_sequence SET next_value = next_value + 1 …` → `INSERT incident`.

**Report SQL:**
```sql
SELECT e.esf_number, e.description,
       COUNT(r.resource_id) AS total_resources,
       COUNT(rr.request_id) AS resources_in_use
FROM esf e
LEFT JOIN resource r          ON r.primary_esf = e.esf_number AND r.owner_username = :username
LEFT JOIN resource_request rr ON rr.resource_id = r.resource_id AND rr.status = 'DEPLOYED'
GROUP BY e.esf_number, e.description
ORDER BY e.esf_number;
```

### 3.10 Testing & quality gates (`./mvnw -B verify`)
- Spotless check (google-java-format) runs in `verify`.
- Unit tests (`*Test`, Surefire): services with Mockito, plus validation and the JWT service.
- Web slice tests (`@WebMvcTest` + `spring-security-test`): status codes, ProblemDetail shape, auth required.
- Integration tests (`*IT`, Failsafe, Testcontainers `mysql:26.10.0.1`, `@ServiceConnection`): Flyway applies cleanly; every repository query; full request lifecycle; incident id concurrency (10 parallel creates → 10 distinct sequential ids); search filters including the legacy bug regressions in §6.
- JaCoCo: merge unit + IT; `check` rule ≥ **80% line coverage** on `service` and `repository` packages, ≥ 70% overall bundle (exclude `ErmsApplication`, `config`, `api.dto`).
- Running Testcontainers locally on Rancher Desktop: `DOCKER_HOST=unix://$HOME/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. Document this in backend/README.md.

### 3.11 Backend image contract (`backend/Dockerfile`)
- Build stage: `FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-26` → `./mvnw -B -DskipTests package` with a BuildKit cache mount for `~/.m2` → `java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /extracted`.
- Runtime: `eclipse-temurin:25-jre-alpine`, user `10001:10001` (numeric `USER`), copy the layers in order (dependencies, spring-boot-loader, snapshot-dependencies, application), `ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]`, `EXPOSE 8080 8081`, `HEALTHCHECK` via `wget -qO- http://127.0.0.1:8081/actuator/health/liveness`. Must run with a read-only root filesystem when `/tmp` is writable. OCI labels (`org.opencontainers.image.source` etc.).
- CI runs tests separately, so the image build skips them.

---

## 4. Frontend

### 4.1 Stack
Exact versions are pinned in `frontend/package.json` (`.npmrc` has `save-exact=true`), and `package-lock.json` is committed.

Vite 8.3 · React 19.3 · **TypeScript 6.0 (strict, plus `noUncheckedIndexedAccess`)** · MUI 9.6 (`@mui/material`, `@mui/icons-material`, emotion) · react-router 7.18 (declarative `<Routes>`) · axios 1.x · react-hook-form 7 (with a resolver adapter around the pure validators) · Vitest 5 + v8 coverage · React Testing Library + `@testing-library/user-event` + MSW 2 + jsdom · ESLint 10 flat config (typescript-eslint `strictTypeChecked` + `stylisticTypeChecked`, react-hooks 7, react-refresh, jsx-a11y `strict`, eslint-config-prettier) · Prettier 3. `.nvmrc` = `24`; the engines field requires Node ≥ 24.15. CRA, `styled-components`, `yarn.lock` and all `@material-ui/*` have been removed.

Version notes: **ESLint 10** is used instead of 9 because 9.x is marked "no longer supported" on npm. `eslint-plugin-jsx-a11y` 6.10.2 still declares `eslint ≤ 9` as a peer, so `package.json` `overrides` points its peer at the installed ESLint. **TypeScript 6.0** is the newest version typescript-eslint supports (`<6.1`). TS 7 (the native port) is not supported yet.

### 4.2 Structure
The layers are shared infrastructure (`api`, `auth`, `components`, `forms`, `hooks`, `validation`, `utils`) plus one folder per feature under `features/`. Each feature folder holds the page, its page-specific components and their tests.
```
src/
  main.tsx                BrowserRouter + <App/>
  App.tsx                 ThemeProvider + CssBaseline + NotificationProvider + AuthProvider + AppRoutes
                          (authenticated pages lazy-loaded with React.lazy)
  theme.ts                legacy colours (#2374AB), system font stack (no web fonts → CSP-friendly)
  types/api.ts            TS mirror of API.md (requests, responses, enums, ProblemDetail)
  api/client.ts           axios instance, baseURL "/api/v1"; request interceptor adds the Bearer token from the
                          stored session (never on /auth/login); response interceptor: 401 from any endpoint except
                          /auth/login → registered unauthorized handler; every error is normalised to ApiError
  api/errors.ts           ApiError {status,title,detail,code,requestId,fieldErrors} + ProblemDetail normaliser
                          (also network/timeout/non-JSON bodies; X-Request-Id header as fallback requestId)
  api/{auth,me,reference,incidents,resources,requests,reports}.ts   one typed function per API.md endpoint (#1–#18)
  auth/session.ts         sessionStorage "erms.auth" = {token,user,expiresAt}; JWT exp decoded (not verified);
                          expired/corrupt entries are removed on read
  auth/AuthContext.tsx    AuthProvider: login(), logout(), isAuthenticated, user; expiry timer; 401 handler →
                          clear session + navigate("/login?expired=1", state.from)
  auth/{context,useAuth}.ts, auth/ProtectedRoute.tsx
  components/             AppLayout (AppBar "ERMS", user name + user-type label, Logout, skip link), PageHeader,
                          ErrorAlert (ProblemDetail title/detail/unmapped field errors/requestId), Loading, EmptyState,
                          ConfirmDialog, NotificationProvider + useNotify (snackbar)
  forms/formUtils.ts      validatorResolver (pure validator → RHF resolver), applyServerFieldErrors
                          (ProblemDetail errors[] → field helper texts; "capabilities[0]" maps to "capabilities")
  hooks/useApiData.ts     loader → {data, error, loading, reload}; aborts on unmount/supersede; keeps stale data
  validation/{common,incident,resource,request,login}.ts   ported legacy rules as pure functions (+ tests)
  utils/{date,format}.ts  local "today", ISO date checks, cost/distance/availability/profile-subtitle formatting
  features/login/LoginPage.tsx
  features/menu/MainMenuPage.tsx
  features/incidents/AddIncidentPage.tsx
  features/resources/{AddResourcePage,CapabilitiesInput,SearchResourcesPage,RequestDialog}.tsx, rowAction.ts, searchParams.ts
  features/requests/{ResourceStatusPage,RequestsTable}.tsx
  features/reports/ResourceReportPage.tsx
  features/errors/NotFoundPage.tsx
  test/setup.ts, test/render.tsx, test/fixtures.ts, test/msw/{handlers,server}.ts
```

### 4.3 Routes & behaviour
| Path | Page | Notes |
|---|---|---|
| `/login` | LoginPage | Username + password (masked). Shows the API's generic error on 401, and a "Session expired" banner for `?expired=1`. On success, redirects to the originally requested location (`state.from`) or `/`. An already signed-in user is redirected away |
| `/` | MainMenuPage | `GET /me`. Shows name, user-type chip, subtitle by type (INDIVIDUAL→jobTitle, COMPANY→headquarters, MUNICIPALITY→municipalityCategory, GOVERNMENT_AGENCY→agencyNameLocalOffice) and the type's other profile fields. Card links to the 5 pages below. Logout is in the app bar on every page |
| `/resources/new` | AddResourcePage | Loads `/me`, `/esfs` and `/cost-units` in parallel. Owner shown read-only ("Name (username)"). Primary ESF select; additional ESFs in an Autocomplete multi-select that excludes the primary (changing the primary removes it from the additional ESFs); capability chips (add with Enter/Add, case-insensitive duplicate check, remove); cost (≤ 2 decimals) + unit select; lat/lon; optional max distance. On 201: success alert "Resource #id created", and the form resets |
| `/incidents/new` | AddIncidentPage | Declaration select "(MD) Major Disaster" (server order = by name); native date input defaulting to today with `max=today`; description (6–500, live counter); lat/lon. Incident ID is shown as "(auto assigned)". On 201: success alert with the **server-generated** incidentId and a link to `/resources/search?incidentId=<id>` |
| `/resources/search` | SearchResourcesPage | Filters: keyword, ESF (Any), incident (my incidents, or none; preselected from `?incidentId=`), "Within N km" (enabled only with an incident; cleared when the incident is cleared). Runs an unfiltered search on load (all resources, F-5 AC1). Paginated MUI table (20/page, server-side): ID, name, owner name, cost/unit, status chip ("Available" / "In use until YYYY-MM-DD"), plus distance and action columns when the search used an incident. Action: **Deploy** if `ownerUsername === me && available`, otherwise **Request** → dialog for expectedReturnDate (≥ today) → `POST /resource-requests` with `deployImmediately` set accordingly. Success snackbar and results refresh; API errors (e.g. 409) stay in the dialog |
| `/resource-status` | ResourceStatusPage | Three independently loaded tables: "Resources in use" (`role=requester&status=DEPLOYED`, Return), "Resources requested by me" (`role=requester&status=PENDING`, Cancel), "Requests received" (`role=owner&status=PENDING`, Deploy (disabled with a tooltip unless `resourceAvailable`) and Reject). Every action asks for confirmation, then shows a success or error snackbar (ProblemDetail `detail`) and refreshes all three tables |
| `/reports/resources-by-esf` | ResourceReportPage | Table of the 15 ESF rows + a totals footer row; a hint with an "Add a resource" link when the caller has no resources |
| `*` | NotFoundPage | Public; links back to `/` |

Every page has loading, empty and error states (the error state has Retry where it helps). Forms validate with the ported pure validators (on submit, then on touch). Server `errors[]` map onto field helper texts, and errors for unknown fields are listed in the page alert together with the `requestId`. There is no `console.log` (`no-console` is an ESLint error) and no hardcoded `http://localhost` in `src/` (an ESLint `no-restricted-syntax` rule enforces this). All controls are labelled, and jsx-a11y `strict` is enforced. A "Skip to main content" link is provided. Client-side "today" uses the browser's time zone; the server (`ERMS_TIME_ZONE`) is authoritative.

### 4.4 Dev & test
- `vite.config.ts`: the dev server (5173) and `vite preview` (4173) proxy `/api` → `process.env.VITE_PROXY_TARGET ?? "http://localhost:8080"`. The build emits no inline scripts or styles, so it works with the CSP below.
- Scripts: `dev`, `build` (`tsc -b && vite build`), `preview`, `test` (`vitest run`), `test:watch`, `test:coverage`, `lint` (`eslint . --max-warnings 0`), `lint:fix`, `format`, `format:check`, `typecheck` (`tsc -b --noEmit`).
- Coverage (`vitest.config.ts`, v8): thresholds of lines/statements/functions ≥ 85% and branches ≥ 80%. This is stricter than the required 70% lines. `main.tsx`, `types/` and `test/` are excluded.
- Tests: see [TESTING.md → Frontend suite](TESTING.md#frontend-suite). Every page is tested through the real route table with MSW handlers for the API.md endpoints.

### 4.5 Frontend image contract (`frontend/Dockerfile`)
- Build: `FROM --platform=$BUILDPLATFORM node:24-alpine` → `npm ci` (BuildKit cache mount) → `npm run build`. Tests and lint run in CI, not in the image build.
- Runtime: `nginxinc/nginx-unprivileged:1.29-alpine`, `USER 10101:10101` (above 10000; nginx only writes to `/tmp`), listens on **8080** (IPv4 only), `EXPOSE 8080`. OCI labels (`title`, `description`, `source`, `version`/`revision` from build args `VERSION`/`REVISION`).
- Config: the image ships its own `/etc/nginx/nginx.conf` (from `nginx/nginx.conf`: pid and all temp paths in `/tmp`, JSON access log to stdout including `request_id`, `server_tokens off`). The base image's entrypoint renders `nginx/default.conf.template` (copied to `/etc/nginx/templates/`) into **`/tmp/default.conf`** (`NGINX_ENVSUBST_OUTPUT_DIR=/tmp`). nginx.conf includes that exact file, so a failed render stops nginx from starting instead of letting it run with no server block. The stock `conf.d/default.conf` is deleted.
- Env: `BACKEND_URL` (default `http://backend:8080`, no trailing slash). `location /api/ { proxy_pass ${BACKEND_URL}; }` forwards the full `/api/v1/...` URI with `Host`, `X-Forwarded-*` and `X-Request-Id` (the caller's id, or nginx's `$request_id`). It is used by docker-compose; on k8s the Ingress routes `/api` directly. nginx resolves the `BACKEND_URL` host **once at start-up**, so the host must resolve when the container starts or nginx exits with `host not found in upstream`. In compose, use `depends_on` on the backend; on k8s the Service name resolves as soon as the Service exists.
- Routes: `location = /healthz { return 200 "ok\n"; }`. SPA fallback `try_files $uri $uri/ /index.html` (deep links such as `/resource-status` serve index.html). `/assets/` uses `try_files $uri =404` with `Cache-Control: public, max-age=31536000, immutable`. Everything else (index.html, healthz) gets `no-cache`, and `/api/` keeps the backend's Cache-Control. Dotfiles → 404. gzip on.
- Security headers (server level, `always`, inherited by every location): `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: geolocation=(), camera=(), microphone=()`. On `/api/` the backend's duplicates of these headers are hidden.
- Read-only root filesystem: **only `/tmp` must be writable**. Mounting emptyDirs/tmpfs at `/var/cache/nginx` and `/etc/nginx/conf.d` as well is harmless (the original contract) but not required. This avoids the docker `--tmpfs` permission problem: a tmpfs over an existing image directory inherits its `root:root 0775` mode and is not writable by the runtime uid. `HEALTHCHECK CMD ["wget","-qO-","http://127.0.0.1:8080/healthz"]` (exec form; hadolint clean).
- Verified: `docker run --user 10101:10101 --read-only --tmpfs /tmp --tmpfs /var/cache/nginx --tmpfs /etc/nginx/conf.d --cap-drop ALL --security-opt no-new-privileges erms-frontend:dev` gives a healthy container, `/healthz` 200, deep links → index.html, headers present, and `/api/` proxied to the upstream.

---

## 5. Platform

### 5.1 docker-compose.yml (local dev, no k8s)
Services: `mysql` (`mysql:26.10.0.1`, db `erms`, user `erms`, healthcheck `mysqladmin ping`, named volume), `backend` (build `./backend`, `SPRING_PROFILES_ACTIVE=demo`, `ERMS_API_DOCS_ENABLED=true`, dev-only placeholder secrets clearly labelled `change-me-…`, `depends_on: mysql: condition: service_healthy`), `frontend` (build `./frontend`, `BACKEND_URL=http://backend:8080`, port `3000:8080`). `http://localhost:3000` must give a fully working app.

### 5.2 Helm chart `deploy/helm/erms`
Files: `Chart.yaml` (apiVersion v2, version 0.1.0, appVersion "0.1.0"), `values.yaml`, `values-local.yaml`, `values.schema.json` (optional, recommended), `templates/`: `_helpers.tpl`, `serviceaccount.yaml` (automount false), `secret.yaml`, `backend-configmap.yaml`, `backend-deployment.yaml`, `backend-service.yaml`, `frontend-deployment.yaml`, `frontend-service.yaml`, `mysql-statefulset.yaml`, `mysql-service.yaml` (headless `erms-mysql-headless` + ClusterIP `erms-mysql`), `ingress.yaml`, `networkpolicy.yaml`, `pdb.yaml` (enabled when replicas > 1), `hpa.yaml` (disabled by default), `NOTES.txt`, `tests/test-health.yaml`.

Key values (`values.yaml`):
```yaml
imageRegistry: ghcr.io/manojis
backend:  { image: { repository: erms-backend,  tag: "", pullPolicy: IfNotPresent }, replicas: 1,
            profiles: "", apiDocsEnabled: false, corsAllowedOrigins: "",
            resources: { requests: { cpu: 250m, memory: 512Mi }, limits: { memory: 1Gi } } }
frontend: { image: { repository: erms-frontend, tag: "", pullPolicy: IfNotPresent }, replicas: 1,
            resources: { requests: { cpu: 50m, memory: 64Mi }, limits: { memory: 128Mi } } }
mysql:    { image: mysql:26.10.0.1, database: erms, username: erms, storage: { size: 2Gi, storageClass: "" },
            resources: { requests: { cpu: 250m, memory: 512Mi }, limits: { memory: 1Gi } } }
secrets:  { existingSecret: "" }      # else chart-managed secret
ingress:  { enabled: true, className: traefik, host: erms.localhost }
networkPolicy: { enabled: true, ingressControllerNamespace: kube-system }
```
`values-local.yaml`: `backend.profiles: demo`, `backend.apiDocsEnabled: true`, `mysql.storage.storageClass: local-path`.

Requirements:
- **Secret** `erms-secrets` keys `mysql-root-password`, `mysql-password`, `jwt-secret`. Use `lookup` to reuse existing values on upgrade, else `randAlphaNum` (48 for JWT, 24 for passwords). Annotate `helm.sh/resource-policy: keep`. Skip the whole template when `secrets.existingSecret` is set.
- **Backend Deployment**: env from the ConfigMap plus secretKeyRefs (`SPRING_DATASOURCE_PASSWORD`, `ERMS_JWT_SECRET`). `SPRING_DATASOURCE_URL=jdbc:mysql://erms-mysql:3306/erms?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`. initContainer `busybox:1.38` waits for `nc -z erms-mysql 3306` (same restricted securityContext). Probes on port `management` (8081): startupProbe `/actuator/health/liveness` (failureThreshold 30, period 5), livenessProbe `/actuator/health/liveness`, readinessProbe `/actuator/health/readiness`. `terminationGracePeriodSeconds: 30`, `preStop` `sleep 5`. Pod annotations `prometheus.io/scrape: "true"`, `prometheus.io/port: "8081"`, `prometheus.io/path: /actuator/prometheus`. Add a `checksum/config` annotation.
- **Frontend Deployment**: env `BACKEND_URL=http://erms-backend:8080`. Probes `/healthz` on 8080. emptyDirs for `/tmp`, `/var/cache/nginx`, `/etc/nginx/conf.d`.
- **Pod security** (all workloads): `runAsNonRoot: true`, `seccompProfile: RuntimeDefault`. Containers: `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `readOnlyRootFilesystem: true` (backend + frontend, with emptyDir `/tmp`). backend uid 10001, frontend uid 10101, mysql `runAsUser/runAsGroup/fsGroup: 999` (mysql gets emptyDirs for `/var/run/mysqld` and `/tmp` if readOnlyRootFilesystem is set; a writable root is acceptable for mysql if needed, but document it).
- **MySQL StatefulSet**: 1 replica, `volumeClaimTemplates` `data` → `/var/lib/mysql`, env `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` from the secret. Readiness/liveness via exec `mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD"` (use `sh -c`).
- **Ingress**: host `erms.localhost`. `/api` Prefix → backend:8080; `/` Prefix → frontend:8080.
- **NetworkPolicy**: mysql accepts 3306 only from backend pods. Backend accepts 8080 from the ingress-controller namespace + frontend pods, and 8080/8081 from pods in the same namespace with label `app.kubernetes.io/component: test` (helm test). Frontend accepts 8080 from the ingress-controller namespace. Use the standard labels `app.kubernetes.io/{name,instance,component,version,managed-by}`, `helm.sh/chart`.
- **helm test** pod (`curlimages/curl`, label component=test): `curl -fsS http://erms-backend:8081/actuator/health/readiness` and `http://erms-frontend:8080/healthz`.
- Must pass `helm lint` and `helm template … | kubeconform -strict -summary -kubernetes-version 1.36.0` (the Rancher Desktop cluster runs k3s v1.36.6).

### 5.3 CI: `.github/workflows/ci.yml` (`name: CI`)
Triggers: `pull_request` (branches: main), `push` (branches: main), `workflow_dispatch`. Top-level `permissions: contents: read`. `concurrency: ci-${{ github.ref }}`, cancel-in-progress for PRs. Pin actions to major versions (Dependabot keeps them updated).

| Job | Runs on | Steps |
|---|---|---|
| `backend` | ubuntu-latest | setup-java temurin 21 + maven cache → `./mvnw -B verify` → upload JaCoCo HTML + surefire/failsafe reports (always) |
| `frontend` | ubuntu-latest | setup-node 26 + npm cache → `npm ci` → `lint` → `format:check` → `typecheck` → `test:coverage` → `build` → upload coverage |
| `helm` | ubuntu-latest | setup-helm → `helm lint deploy/helm/erms -f deploy/helm/erms/values-local.yaml` → template with `--set backend.image.tag=ci --set frontend.image.tag=ci` → kubeconform |
| `dockerfiles` | ubuntu-latest | hadolint for `backend/Dockerfile` and `frontend/Dockerfile` (`failure-threshold: warning`) |
| `mysql-upgrade` | ubuntu-latest | `scripts/mysql-upgrade-test.sh`: seed on the base commit's MySQL, upgrade in place via `MYSQL_UPGRADE_VIA` LTS steps to the chart's version, verify version, `CHECK TABLE`, data checksums |
| `security` | ubuntu-latest | Trivy `fs` scan (vuln, secret, misconfig; `severity: CRITICAL,HIGH`; `ignore-unfixed: true`) → SARIF upload (`permissions: security-events: write` on this job). Fails on CRITICAL |
| `images` | ubuntu-latest, `needs: [backend, frontend, helm, dockerfiles, security]` | setup-qemu + setup-buildx → **on push to main**: login ghcr with `GITHUB_TOKEN` (`permissions: packages: write`), build-push both images, `platforms: linux/amd64,linux/arm64`, tags `ghcr.io/${{ github.repository_owner }}/erms-{backend,frontend}:${{ github.sha }}` and `:latest`, `cache-from/to: type=gha`, `provenance: true`, `sbom: true` → Trivy image scan of the pushed sha tag (fail on fixable CRITICAL). **On PR**: build linux/amd64 only with `push: false, load: true` and Trivy-scan the local image |

`.github/workflows/codeql.yml`: languages `java-kotlin` (build-mode autobuild or manual `./mvnw -B -DskipTests package`) and `javascript-typescript`. Runs on push/PR to main and weekly. `.github/dependabot.yml`: ecosystems `maven` (/backend), `npm` (/frontend), `docker` (/backend, /frontend), `github-actions` (/), all weekly, with grouped minor/patch updates.

### 5.4 CD: `.github/workflows/cd.yml` (`name: CD`)
```yaml
on:
  workflow_run: { workflows: [CI], types: [completed], branches: [main] }
  workflow_dispatch: { inputs: { image_tag: { description: "Image tag (git sha)", required: false } } }
```
- Job `deploy-local`: `if: github.event_name == 'workflow_dispatch' || (github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push' && github.event.workflow_run.head_repository.full_name == github.repository)`.
- `runs-on: [self-hosted, macOS, ARM64, rancher-desktop]`, `environment: { name: local, url: http://erms.localhost }`, `concurrency: { group: deploy-local, cancel-in-progress: false }`, `permissions: { contents: read }`, `timeout-minutes: 20`.
- Steps: checkout `ref: ${{ github.event.workflow_run.head_sha || github.sha }}` → resolve `TAG` (input, else head_sha) → preflight (`kubectl version`, `helm version`, `kubectl auth can-i create deployments -n erms`) → `helm upgrade --install erms deploy/helm/erms --kube-context rancher-desktop -n erms -f deploy/helm/erms/values-local.yaml --set backend.image.tag=$TAG --set frontend.image.tag=$TAG --rollback-on-failure --wait --timeout 10m` (Helm 4 renamed `--atomic`; the scripts choose the flag from the Helm major version) → `helm test erms -n erms --kube-context rancher-desktop --logs` → `scripts/smoke-test.sh http://erms.localhost` → on failure: `scripts/diagnostics.sh` (pods, events, describe, logs). No docker: k3s pulls the public `ghcr.io/manojis/erms-*:$TAG` images itself. The environment `local` requires the owner's approval before the job starts.
- **Never** pass a kube context through a global `kubectl config use-context`. Always use the explicit `--kube-context`/`--context` flag.
- Health over the ingress: the management port is deliberately **not** exposed through the ingress. Readiness is covered by `helm test` (in-cluster), and the public path is covered by the smoke test.

`scripts/smoke-test.sh <baseUrl>` (bash, `set -euo pipefail`, retries with backoff for up to 60s): `GET /` → 200 and HTML containing `<div id="root">`; `POST /api/v1/auth/login` as `alice`/`password` → 200 + token; `GET /api/v1/me` with the token → 200 and `username == alice`; `GET /api/v1/esfs` → 15 items; `GET /api/v1/me` without a token → 401.

`scripts/deploy-local.sh`: the same deploy without CI. Builds both images locally with `docker build` (tag `local-$(git rev-parse --short HEAD)`), then helm upgrade, then smoke test. Exposed as `make deploy-local`.

### 5.5 Self-hosted runner: `scripts/setup-local-runner.sh`
Idempotent, `set -euo pipefail`, run as the normal user (uses `sudo` for the privileged steps). Flags `--repo owner/name` (default from `gh repo view`), `--name`, `--skip-hardening`, `--start`, `--stop`, `--status`, `--uninstall`.
1. Preflight: macOS arm64; `gh auth status` + repo admin; `kubectl`/`helm`; `kubectl --context rancher-desktop get nodes`; `sudo -v`.
2. Hidden role account `_ermsrunner` via `dscl` (free uid/gid in 450–499, `Password *`, `UserShell /usr/bin/false`, `IsHidden 1`, home `/opt/erms-runner/home`); refuse if it is in `admin`. Directories: `/opt/erms-runner` and `/opt/erms-runner/bin` root:wheel 755; `runner/` and `home/` `_ermsrunner` 700; `/Library/Logs/erms-runner` 750.
3. Tools: `kubectl` matching the server version from `dl.k8s.io` (SHA-256 verified) and Rancher Desktop's `helm`, installed root-owned in `/opt/erms-runner/bin`.
4. `kubectl apply -f deploy/runner/deployer-rbac.yaml` (namespace `erms` with PSA `restricted`, ServiceAccount `erms-deployer`, namespaced Role/RoleBinding, token Secret). Write `home/.kube/config` (context `rancher-desktop`, the SA token, mode 600) and verify with `kubectl auth can-i` (deployments in `erms`: yes; nodes and kube-system: no).
5. Latest `actions/runner` osx-arm64 tarball via `gh api repos/actions/runner/releases/latest`, SHA-256 verified against the release body and the asset digest, extracted as `_ermsrunner`.
6. Registration token from `gh api -X POST repos/$REPO/actions/runners/registration-token`; `config.sh --unattended --replace --labels rancher-desktop --name "$(scutil --get LocalHostName)-rancher-desktop" --work _work`, run as `_ermsrunner` (skipped if already registered).
7. `.path` = `/opt/erms-runner/bin:/usr/bin:/bin:/usr/sbin:/sbin`, `.env` with `HOME` and `KUBECONFIG`; LaunchDaemon `/Library/LaunchDaemons/dev.erms.actions-runner.plist` (`UserName`/`GroupName` `_ermsrunner`, `runsvc.sh`, `RunAtLoad`, `KeepAlive` on failure), loaded with `launchctl bootstrap system`. `--stop` = `bootout` + `disable`; `--start` = `enable` + `bootstrap`.
8. **Public-repo hardening** via `gh api`: environment `local` with the owner as required reviewer (`prevent_self_review: false`) and a custom deployment branch policy `main`; fork-PR workflow approval for all external contributors; read-only default `GITHUB_TOKEN`; secret scanning + push protection; a reminder that only `cd.yml` may use this runner.
9. `--uninstall`: unload and delete the LaunchDaemon, `config.sh remove` with a remove token, delete `/opt/erms-runner`, the logs, the user/group and the ServiceAccount + RBAC (not the namespace or the app).

### 5.6 Makefile targets
`help` (default), `be-test`, `be-build`, `fe-install`, `fe-test`, `fe-build`, `lint`, `up` / `down` (compose), `images`, `helm-lint`, `deploy-local`, `smoke`, `runner-setup`, `runner-uninstall`.

---

## 6. Legacy defects: required fixes and regression tests
Each item gets a fix plus (where marked **T**) an automated test. IDs are referenced by QA.

**Security**
- S1 Hardcoded DB URL/user/password in 4 classes → env config (§3.3). Grep test in QA: no `gatech123` anywhere.
- S2 Plaintext passwords stored and compared in SQL; `verifyUser` printed passwords to stdout → BCrypt + no credential logging. **T**
- S3 No authentication; the client supplies `username` (path/query/body `requestedBy_username`) → anyone can act as anyone (IDOR) → JWT identity only. **T** (another user's incident in search → 404; acting on someone else's request → 403/404)
- S4 `@CrossOrigin` wildcard on every endpoint → configurable allow-list, off by default. **T**
- S5 `useSSL=false` hardcoded → driver defaults / configurable URL.
- S6 Exception handler returned raw `e.getMessage()` → generic 500 detail. **T**
- S7 Login returned 200 `{success:false}` → 401 ProblemDetail. **T**
- S8 The frontend's only "auth" was username in sessionStorage → JWT + 401 handling.
- S9 LIKE wildcards in keyword not escaped (`%` matches everything) → escape. **T**
- S10 EOL dependencies with known CVEs (Boot 2.0.2, jackson-databind 2.9.5, mysql-connector 8.0.11, axios 0.18, react-scripts 1.1.4) → current versions + Dependabot + Trivy.

**Reliability**
- R1 A new `DriverManager` connection per call; many paths never close connections/statements/result sets (getAllIncident, getReport, getTotalESF, getTotalAvailable, InsertIncident, ErmsUtil.*) → HikariCP + JdbcTemplate.
- R2 Shared mutable fields in singletons (`conn`, `report`, `ESFs`, `errorResponse` in the ControllerAdvice) → thread-safety bugs → stateless beans.
- R3 `SqlConnection.date` computed once at bean creation, so every request/deploy/return date was the server start date → `Clock`-based today. **T**
- R4 `getDBConnection` swallowed errors and returned null → NPE.
- R5 `SQLException` swallowed everywhere → 200 with empty/partial/null data → exceptions propagate to the handler → 500/409.
- R6 Multi-step writes not atomic (resource + capabilities + ESFs on 3 connections; request insert + resource update; deploy; return; reject) → `@Transactional`. **T** (an invalid capability rolls back the resource)
- R7 `addResource` NPE when `capabilities`/`additional_esf` is null; `setFloat` used for `primary_esf` → null-safe lists, correct types. **T**
- R8 Domain models annotated `@Repository` and autowired as singletons; `SqlConstants` is a `@Service` with mutable public statics → records, constants inside repositories.
- R9 Misused `@Scope("request")` / `@EnableAutoConfiguration` on the controller; dead `TestingMain` in prod sources; unused `log4j2.xml`; System.out logging with wrong method names → removed.
- R10 The only test (`contextLoads`) needed a live DB → Testcontainers.
- R11 README says port 8080 then 8090; the UI hardcodes `http://localhost:8090` → relative `/api`.

**Functional**
- F1 Keyword search: `a.id=c.id AND cap LIKE ? OR name LIKE ? OR model LIKE ?`. AND/OR precedence creates a cross join for name/model matches and returns nothing when there are no capability rows → `name OR model OR EXISTS(capability)`. **T** (a resource with no capabilities is found by name; no duplicates)
- F2 ESF search: implicit cross join with `AdditionalESF`, so a primary-ESF match is missed when additional_esf is empty → `primary = ? OR EXISTS(additional)`. **T**
- F3 ESF+keyword search combines F1 and F2. **T**
- F4 Distance via `acos(...)` can exceed 1 → NULL for co-located points; `HAVING DISTANCE < max_distance` drops resources with NULL max distance → `ST_Distance_Sphere`, null = unlimited, inclusive `<=`. **T** (a resource at the exact incident location is returned with distance 0.0)
- F5 `getAllResource` branching: with incident+distance plus only one of keyword/esf, the result is overwritten by location-only search, so the keyword is ignored → a single AND-combined query. **T**
- F6 `getResourceDetails`: N+1 queries (one per id) and a comparator `(int)(d1-d2)` that mis-orders sub-km differences → one query, ORDER BY distance. **T**
- F7 Incident insert swaps lat/long (columns `longitude,latitude` bound as latitude,longitude) → correct binding. **T** (round-trip)
- F8 Incident id = client-side `COUNT(*)+1`, then client-supplied → race and duplicate PK; the client controls ids → server-side `incident_sequence` row lock. **T** (concurrency)
- F9 Inconsistent date formats (`MM-dd-yyyy` for incidents, `yyyy-MM-dd` elsewhere), `java.sql.Date` in the API → ISO `LocalDate` everywhere.
- F10 Report SQL uses `GROUP BY … ASC`, which was removed in MySQL 8.0.13, so the query fails; `is_available='false'` compares a tinyint to a string; pointless self-join; N+1 `getESF` per row → §3.9 report SQL. **T**
- F11 "totalAvailable" endpoint actually counted in-use resources (misnamed) → `totals.resourcesInUse`.
- F12 Return updated `actual_return_date` on **all** RequestedBy rows for the resource (every historical incident), with no ownership check → only the target request, requester/owner only. **T**
- F13 Deploy: status taken from client `actionStatus` (arbitrary/NULL); no check that the resource is free, so double deployment was possible → server state machine + `uq_request_one_deployment`. **T**
- F14 Request blocked forever once **any** row existed for resource+incident (even rejected/returned); the request set `Resource.next_available` before acceptance → block only active (PENDING/DEPLOYED) pairs; derived availability. **T**
- F15 Reject set `next_available = NULL` even while the resource was deployed elsewhere, corrupting availability → status only. **T**
- F16 Cancel deleted the row (losing the audit trail) with no ownership check → requester-only, `CANCELLED`. **T**
- F17 Received requests included rejected ones (filtered only on `deploy_date IS NULL`) → `status = PENDING`. **T**
- F18 "In use" list keyed on the resource's current availability, not request status → stale rows after redeploy → `status = DEPLOYED`. **T**
- F19 User not found → HTTP **204 with a body** → 404 ProblemDetail (`/me` can't hit this in practice; general rule). **T** on another endpoint
- F20 Type mismatch / NumberFormatException → **200** with an error body → 400. **T**
- F21 Every search param `required=true`, so omitting any → error → all optional. **T**
- F22 `cost` was `int`, lat/long `float` (precision loss) → `DECIMAL(12,2)`, `DECIMAL(9,6)`.
- F23 `HashMap` responses gave unordered dropdowns → ordered arrays.
- F24 UI called `api//recievedrequest` (double slash, misspelled) → new contract.

---

## 7. Security baseline (OWASP Top 10 2021 mapping)
| OWASP | Control |
|---|---|
| A01 Broken Access Control | JWT identity; ownership checks in services; 404 for non-visible; no client-supplied usernames; deny by default |
| A02 Cryptographic Failures | BCrypt; JWT secret ≥ 256 bits from a Secret; no secrets in git; TLS termination point = ingress (local: http only, documented) |
| A03 Injection | Named parameters only; LIKE escaping; no string-concatenated SQL from input; React auto-escaping + CSP |
| A04 Insecure Design | Explicit state machine; DB constraints back up business rules |
| A05 Security Misconfiguration | Management port internal; docs off by default; non-root read-only containers; security headers; NetworkPolicies |
| A06 Vulnerable Components | Current versions; Dependabot; Trivy fs+image; CodeQL |
| A07 Identification & Auth Failures | Generic login errors; constant-time miss path; short-lived tokens; 401 → client logout |
| A08 Software & Data Integrity | Pinned images by sha tag; SBOM + provenance; CD only from main push of this repo; fork-PR approval |
| A09 Logging & Monitoring | Structured JSON logs with requestId; no PII/credentials in logs; Prometheus metrics; audit trail in resource_request |
| A10 SSRF | No user-controlled outbound requests |

Known limitations (accepted for local scope): no TLS on `erms.localhost`, no login rate limiting (documented as future work: bucket4j or ingress middleware), single MySQL replica, no refresh tokens.
