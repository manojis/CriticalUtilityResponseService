# ERMS REST API v1

This is the **source of truth** for the HTTP contract. The backend implements it exactly, and the frontend types (`frontend/src/types/api.ts`) mirror it. The machine-readable OpenAPI is served by the backend at `/api/openapi`, with Swagger UI at `/api/docs` when `ERMS_API_DOCS_ENABLED=true`. QA checks that it matches this document.

Owner: backend developer (WP-BE). Any API change updates this file in the same PR.

## 1. Conventions
- Base URL: `/api/v1` (behind the ingress: `http://erms.localhost/api/v1`; docker-compose: `http://localhost:3000/api/v1` via nginx, or `http://localhost:8080/api/v1` directly).
- JSON bodies use camelCase. Unknown request fields are ignored. Nulls are omitted from responses.
- Dates: `YYYY-MM-DD` (ISO-8601 `LocalDate`). "Today" is computed in `ERMS_TIME_ZONE` (default UTC).
- Money: JSON number with 2 decimals. Coordinates: JSON numbers, 6 decimals of precision.
- Every response carries an `X-Request-Id` header (echoed from the request if supplied, else generated).
- Collections are ordered as documented. Only resource search is paginated.

## 2. Authentication
1. `POST /api/v1/auth/login` with username/password → `accessToken` (HS256 JWT, default TTL 1 h).
2. Send `Authorization: Bearer <accessToken>` on every other call.
3. Token claims: `iss` (=`erms`), `sub` (username), `iat`, `exp`, `name`, `userType`.
4. The caller's identity is **always** taken from `sub`. No endpoint accepts a username from the client.
5. Missing, expired or invalid token → `401` ProblemDetail `auth.unauthorized`. The SPA logs out on any 401.
6. There's no logout endpoint; the client discards the token (stateless, short-lived).

## 3. Error model: RFC 9457 ProblemDetail
`Content-Type: application/problem+json`. Body: `type` (`about:blank`), `title`, `status`, `detail`, `instance`, plus extension properties `code` and `requestId`, and, for validation errors, `errors: [{ "field": "latitude", "message": "must be between -90 and 90" }]`.

| Situation | Status | `code` |
|---|---|---|
| Bean validation / malformed JSON / type mismatch / bad query param | 400 | `validation.failed` / `request.malformed` |
| Missing or invalid token | 401 | `auth.unauthorized` |
| Bad credentials | 401 | `auth.invalid_credentials` |
| Authenticated but not allowed (e.g. requester tries to deploy) | 403 | `auth.forbidden` |
| Unknown entity, or entity not visible to the caller | 404 | `resource.not_found` |
| Illegal state transition, duplicate active request, resource already deployed, unique key violation | 409 | `state.conflict` |
| Anything else | 500 | `internal.error` (generic detail; never echo exception messages) |

## 4. Endpoints
Base path `/api/v1`. JSON is camelCase. Dates use `YYYY-MM-DD` (`LocalDate`). Every endpoint except login requires a Bearer token. "Caller" means the JWT `sub`.

**Enums.** `userType`: `INDIVIDUAL | MUNICIPALITY | GOVERNMENT_AGENCY | COMPANY`. `status` (request): `PENDING | DEPLOYED | REJECTED | CANCELLED | RETURNED`.

| # | Method & path | Request | 2xx response | Errors |
|---|---|---|---|---|
| 1 | `POST /auth/login` | `{"username":"alice","password":"password"}` (both `@NotBlank`, max 50/100) | **200** `{"accessToken":"…","tokenType":"Bearer","expiresIn":3600,"user":{"username":"alice","name":"Alice Smith","userType":"INDIVIDUAL"}}` | 400, 401 |
| 2 | `GET /me` | – | **200** `UserProfile`: `{"username","name","userType","jobTitle"?, "hireDate"?, "municipalityCategory"?, "agencyNameLocalOffice"?, "headquarters"?, "numberOfEmployees"?}` (only the fields for the caller's type, nulls omitted) | 401 |
| 3 | `GET /esfs` | – | **200** `[{"esfNumber":1,"description":"Transportation"}, …]` ordered by esfNumber | 401 |
| 4 | `GET /cost-units` | – | **200** `["Day","Each","Hour","Week"]` alphabetical | 401 |
| 5 | `GET /declarations` | – | **200** `[{"abbreviation":"MD","name":"Major Disaster"}, …]` ordered by name | 401 |
| 6 | `POST /incidents` | `{"declarationAbbreviation":"MD","incidentDate":"2026-11-20","description":"Flooding on I-75","latitude":33.7490,"longitude":-84.3880}` — declaration must exist (else 400); date not after today; description 6–500 chars; lat −90..90; lon −180..180 | **201** + `Location: /api/v1/incidents/MD-3`, body `Incident` `{"incidentId":"MD-3","declarationAbbreviation","incidentDate","description","latitude","longitude","ownerUsername"}` | 400, 401 |
| 7 | `GET /incidents` | – | **200** `[Incident]` owned by caller, ordered incidentDate desc, incidentId desc | 401 |
| 8 | `GET /incidents/{incidentId}` | – | **200** `Incident` (owner only) | 401, 404 |
| 9 | `POST /resources` | `{"resourceName":"Hydraulic Crane","model":"XL-200","primaryEsf":3,"additionalEsfs":[1,7],"capabilities":["Lift 20t","Night ops"],"cost":150.00,"costUnit":"Hour","latitude":33.77,"longitude":-84.39,"maxDistanceKm":120}`. Rules: name 3–100; model ≤100 optional; ESFs must exist; additionalEsfs unique, must not contain primaryEsf, may be empty or omitted; capabilities may be empty or omitted, each 1–100 chars, deduplicated case-insensitively; cost ≥ 0 (2 decimals); costUnit must exist; maxDistanceKm optional, > 0 when present (null = no limit) | **201** + `Location`, body `ResourceDetail` `{"resourceId","resourceName","model","ownerUsername","ownerName","primaryEsf":{"esfNumber","description"},"additionalEsfs":[{…}],"capabilities":[…],"cost","costUnit","latitude","longitude","maxDistanceKm","available":true,"nextAvailableDate":null}` | 400, 401 |
| 10 | `GET /resources/{resourceId}` | – | **200** `ResourceDetail` (any authenticated user) | 401, 404 |
| 11 | `GET /resources?keyword=&esf=&incidentId=&distanceKm=&page=0&size=20` | All optional. `distanceKm` requires `incidentId` (else 400). `incidentId` must be the caller's own incident (else 404). `size` 1..100, default 20 | **200** `Page<ResourceSearchResult>`: `{"content":[{"resourceId","resourceName","ownerUsername","ownerName","cost","costUnit","available","nextAvailableDate","distanceKm"}],"page":0,"size":20,"totalElements":42,"totalPages":3}`. `distanceKm` is null when there's no incidentId; otherwise rounded to 0.1 | 400, 401, 404 |
| 12 | `POST /resource-requests` | `{"resourceId":12,"incidentId":"MD-3","expectedReturnDate":"2026-12-10","deployImmediately":false}`. Caller must own the incident (else 404). expectedReturnDate ≥ today. `deployImmediately=true` is allowed only if the caller owns the resource (else 403); it creates the request already `DEPLOYED` | **201** + `Location`, body `ResourceRequest` | 400, 401, 403, 404, 409 (active request already exists for this resource+incident; or deployImmediately while the resource is deployed) |
| 13 | `GET /resource-requests?role=requester\|owner&status=PENDING,DEPLOYED` | `role` required. `status` optional, comma-separated, default all. `requester` = requests the caller made; `owner` = requests for the caller's resources | **200** `[ResourceRequest]` ordered requestDate desc, requestId desc | 400, 401 |
| 14 | `POST /resource-requests/{id}/deploy` | – | **200** `ResourceRequest` | 401, 403 (not the resource owner), 404, 409 (not PENDING, or resource currently deployed) |
| 15 | `POST /resource-requests/{id}/reject` | – | **200** | 401, 403 (not the owner), 404, 409 (not PENDING) |
| 16 | `POST /resource-requests/{id}/cancel` | – | **200** | 401, 403 (not the requester), 404, 409 (not PENDING) |
| 17 | `POST /resource-requests/{id}/return` | – | **200** | 401, 403 (neither requester nor owner), 404, 409 (not DEPLOYED) |
| 18 | `GET /reports/resources-by-esf` | – | **200** `{"rows":[{"esfNumber":1,"esfDescription":"Transportation","totalResources":2,"resourcesInUse":1}, … all 15 ESFs, zeros included],"totals":{"totalResources":5,"resourcesInUse":2}}`. Covers resources owned by the caller, grouped by primary ESF | 401 |

`ResourceRequest` = `{"requestId","resourceId","resourceName","ownerUsername","incidentId","incidentDescription","requestedBy","status","requestDate","expectedReturnDate","deployDate","actualReturnDate","resourceAvailable"}`. `resourceAvailable` means no DEPLOYED request currently exists for that resource. The UI uses it to enable "Deploy". A request is visible (for GET and actions) only to its requester or the resource owner. Anyone else gets 404.

### 4.1 Visibility & authorization summary
| Entity | Who can read | Who can mutate |
|---|---|---|
| Reference data (ESF, cost units, declarations) | any authenticated user | nobody (Flyway-managed) |
| Incident | owner only (others get 404) | owner creates; no update/delete in v1 |
| Resource | any authenticated user | owner creates; no update/delete in v1 |
| Resource request | requester or resource owner (others get 404) | requester: create, cancel, return. Owner: deploy, reject, return |
| Report | caller's own resources only | – |

### 4.2 Request state transitions
See the state diagram in [FLOWS.md](FLOWS.md#resource-request-lifecycle). Allowed: `PENDING→DEPLOYED` (owner, only if the resource has no other DEPLOYED request), `PENDING→REJECTED` (owner), `PENDING→CANCELLED` (requester), `DEPLOYED→RETURNED` (requester or owner). Anything else → `409 state.conflict`.

## 5. Examples

### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"alice","password":"password"}
```
```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-Id: 6f1c…

{"accessToken":"eyJhbGciOiJIUzI1NiJ9…","tokenType":"Bearer","expiresIn":3600,
 "user":{"username":"alice","name":"Alice Smith","userType":"INDIVIDUAL"}}
```

### Bad credentials
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json

{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Invalid username or password",
 "instance":"/api/v1/auth/login","code":"auth.invalid_credentials","requestId":"6f1c…"}
```

### Validation error
```http
POST /api/v1/incidents
Authorization: Bearer …

{"declarationAbbreviation":"MD","incidentDate":"2026-11-20","description":"x","latitude":95,"longitude":-84.38}
```
```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"type":"about:blank","title":"Bad Request","status":400,"detail":"Request validation failed",
 "instance":"/api/v1/incidents","code":"validation.failed","requestId":"…",
 "errors":[{"field":"description","message":"size must be between 6 and 500"},
           {"field":"latitude","message":"must be less than or equal to 90"}]}
```

### Create incident
```http
POST /api/v1/incidents
Authorization: Bearer …

{"declarationAbbreviation":"MD","incidentDate":"2026-11-20","description":"Flooding on I-75","latitude":33.749,"longitude":-84.388}
```
```http
HTTP/1.1 201 Created
Location: /api/v1/incidents/MD-2

{"incidentId":"MD-2","declarationAbbreviation":"MD","incidentDate":"2026-11-20","description":"Flooding on I-75",
 "latitude":33.749,"longitude":-84.388,"ownerUsername":"alice"}
```

### Search near an incident
```http
GET /api/v1/resources?keyword=crane&incidentId=MD-2&distanceKm=50&page=0&size=20
Authorization: Bearer …
```
```json
{"content":[{"resourceId":1001,"resourceName":"Hydraulic Crane","ownerUsername":"acme_rescue","ownerName":"Acme Rescue Co",
  "cost":150.00,"costUnit":"Hour","available":true,"distanceKm":3.2}],
 "page":0,"size":20,"totalElements":1,"totalPages":1}
```

### Request, then deploy
```http
POST /api/v1/resource-requests
Authorization: Bearer <alice>

{"resourceId":1001,"incidentId":"MD-2","expectedReturnDate":"2026-12-10"}
```
```json
{"requestId":7,"resourceId":1001,"resourceName":"Hydraulic Crane","ownerUsername":"acme_rescue","incidentId":"MD-2",
 "incidentDescription":"Flooding on I-75","requestedBy":"alice","status":"PENDING","requestDate":"2026-11-25",
 "expectedReturnDate":"2026-12-10","resourceAvailable":true}
```
```http
POST /api/v1/resource-requests/7/deploy
Authorization: Bearer <acme_rescue>
```
→ `200` with `"status":"DEPLOYED","deployDate":"2026-11-25","resourceAvailable":false`.

### Illegal transition
`POST /api/v1/resource-requests/7/cancel` after deploy → `409` `{"code":"state.conflict","detail":"Request 7 is DEPLOYED; cannot cancel", …}`.

## 6. Legacy endpoint mapping (removed)
| Legacy | v1 replacement |
|---|---|
| `POST /api/login` | `POST /api/v1/auth/login` |
| `GET /api/user/{username}` | `GET /api/v1/me` |
| `GET /api/esfs`, `/api/units`, `/api/declarations` | `GET /api/v1/esfs`, `/cost-units`, `/declarations` |
| `GET /api/incidentcount?abbr=` | removed (the server generates the id on create) |
| `POST /api/incident`, `GET /api/incident?username=` | `POST /api/v1/incidents`, `GET /api/v1/incidents` |
| `POST /api/resource`, `GET /api/resource?keyword&esf&location&incidentid` | `POST /api/v1/resources`, `GET /api/v1/resources?keyword&esf&incidentId&distanceKm&page&size` |
| `POST /api/requestaction` (`action`=request/deploy/reject/cancel/return) | `POST /api/v1/resource-requests` and `POST /api/v1/resource-requests/{id}/{deploy\|reject\|cancel\|return}` |
| `POST /api/requestresource`, `GET /api/recievedrequest?username=` | `GET /api/v1/resource-requests?role=requester\|owner&status=…` |
| `GET /api/report`, `/api/total/esfs`, `/api/total/available` | `GET /api/v1/reports/resources-by-esf` |
