# ERMS Flows

Sequence and state diagrams for every main flow. Participants: **SPA** (React in the browser), **Ingress** (Traefik), **API** (Spring Boot), **DB** (MySQL). The Ingress is omitted after the first diagram; every `/api` call goes through it.

## Login and JWT

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SPA
    participant ING as Ingress (Traefik)
    participant API as API (AuthController/AuthService)
    participant DB as MySQL
    U->>SPA: enter username + password
    SPA->>ING: POST /api/v1/auth/login
    ING->>API: forward
    API->>DB: SELECT username, name, password_hash, user_type FROM app_user WHERE username=?
    alt user found and BCrypt matches
        API->>API: sign HS256 JWT (sub, name, userType, iss, iat, exp=+1h)
        API-->>SPA: 200 {accessToken, expiresIn, user}
        SPA->>SPA: store {token,user} in sessionStorage "erms.auth"
        SPA-->>U: redirect to / (or original route)
    else unknown user (dummy BCrypt check, constant time) or wrong password
        API-->>SPA: 401 ProblemDetail auth.invalid_credentials
        SPA-->>U: "Invalid username or password"
    end
    Note over SPA,API: Every later call sends Authorization: Bearer token
    SPA->>API: GET /api/v1/me (Bearer)
    API->>API: validate signature, iss, exp → principal = sub
    alt token invalid/expired
        API-->>SPA: 401 auth.unauthorized
        SPA->>SPA: clear auth, go to /login?expired=1
    else valid
        API->>DB: user + subtype row
        API-->>SPA: 200 UserProfile
    end
```

## Add incident (server-generated ID)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SPA
    participant API as IncidentService
    participant DB as MySQL
    SPA->>API: GET /api/v1/declarations
    API-->>SPA: [{abbreviation,name}]
    U->>SPA: fill form (client validation)
    SPA->>API: POST /api/v1/incidents {declarationAbbreviation, incidentDate, description, latitude, longitude}
    API->>API: Bean Validation (date ≤ today, lat/lon ranges, description 6–500)
    alt invalid
        API-->>SPA: 400 validation.failed + errors[]
    else valid
        API->>DB: BEGIN
        API->>DB: SELECT next_value FROM incident_sequence WHERE declaration_abbreviation=? FOR UPDATE
        Note right of DB: row lock serialises concurrent creates
        API->>DB: UPDATE incident_sequence SET next_value = next_value + 1
        API->>DB: INSERT incident (incident_id = 'MD-' + n, owner = JWT sub, ...)
        API->>DB: COMMIT
        API-->>SPA: 201 Location: /api/v1/incidents/MD-n, body Incident
        SPA-->>U: "Incident MD-n created"
    end
```

## Add resource

```mermaid
sequenceDiagram
    autonumber
    participant SPA
    participant API as ResourceService
    participant DB as MySQL
    par load form data
        SPA->>API: GET /api/v1/me
        SPA->>API: GET /api/v1/esfs
        SPA->>API: GET /api/v1/cost-units
    end
    SPA->>API: POST /api/v1/resources {resourceName, model, primaryEsf, additionalEsfs[], capabilities[], cost, costUnit, latitude, longitude, maxDistanceKm}
    API->>API: validate (ESFs/unit exist, additional ≠ primary, dedupe capabilities)
    API->>DB: BEGIN
    API->>DB: INSERT resource (owner = JWT sub) → resource_id
    API->>DB: batch INSERT resource_capability
    API->>DB: batch INSERT additional_esf
    alt any failure
        API->>DB: ROLLBACK (nothing persisted)
        API-->>SPA: 400 / 409 / 500 ProblemDetail
    else ok
        API->>DB: COMMIT
        API-->>SPA: 201 ResourceDetail
    end
```

## Search resources

```mermaid
sequenceDiagram
    autonumber
    participant SPA
    participant API as ResourceService
    participant DB as MySQL
    SPA->>API: GET /api/v1/incidents (populate "my incidents")
    SPA->>API: GET /api/v1/resources?keyword&esf&incidentId&distanceKm&page&size
    alt distanceKm without incidentId
        API-->>SPA: 400
    else incidentId not owned by caller
        API-->>SPA: 404
    else
        API->>DB: one SELECT … WHERE (keyword: name/model LIKE OR EXISTS capability)<br/>AND (esf: primary = ? OR EXISTS additional_esf)<br/>[+ ST_Distance_Sphere to incident, max_distance_km, distanceKm]<br/>ORDER BY distance|name LIMIT/OFFSET
        API->>DB: matching COUNT(*)
        API-->>SPA: 200 Page<ResourceSearchResult> (available, nextAvailableDate, distanceKm)
        SPA->>SPA: per row: owner==me && available → "Deploy", else "Request" (needs incident)
    end
```

## Request → deploy → return

```mermaid
sequenceDiagram
    autonumber
    actor R as Requester (incident owner)
    actor O as Resource owner
    participant SPA
    participant API as ResourceRequestService
    participant DB as MySQL
    R->>SPA: Request resource 1001 for MD-2, expected return 2026-12-10
    SPA->>API: POST /api/v1/resource-requests {resourceId, incidentId, expectedReturnDate}
    API->>DB: check incident owner = caller, resource exists
    API->>DB: INSERT resource_request (status PENDING, request_date = today)
    alt active request already exists (uq_request_active_pair)
        API-->>SPA: 409 state.conflict
    else
        API-->>SPA: 201 ResourceRequest(PENDING)
    end
    O->>SPA: Resource Status → "Requests received"
    SPA->>API: GET /api/v1/resource-requests?role=owner&status=PENDING
    O->>SPA: Deploy
    SPA->>API: POST /api/v1/resource-requests/7/deploy
    API->>API: caller must be resource owner (else 403)
    API->>DB: UPDATE … SET status='DEPLOYED', deploy_date=today WHERE request_id=7 AND status='PENDING'
    alt 0 rows (not PENDING) or uq_request_one_deployment violated (deployed elsewhere)
        API-->>SPA: 409 state.conflict
    else
        API-->>SPA: 200 ResourceRequest(DEPLOYED)
    end
    R->>SPA: Resource Status → "Resources in use" → Return
    SPA->>API: POST /api/v1/resource-requests/7/return
    API->>DB: UPDATE … SET status='RETURNED', actual_return_date=today WHERE request_id=7 AND status='DEPLOYED'
    API-->>SPA: 200 ResourceRequest(RETURNED) — resource available again
```

Owner self-deploy shortcut: when the caller owns both the resource and the incident, the SPA sends `deployImmediately: true`. The request is inserted directly as `DEPLOYED`, and the API returns 409 if the resource is already deployed.

## Reject and cancel

```mermaid
sequenceDiagram
    autonumber
    actor R as Requester
    actor O as Owner
    participant API as ResourceRequestService
    participant DB as MySQL
    O->>API: POST /resource-requests/{id}/reject
    API->>API: caller == resource owner? else 403
    API->>DB: UPDATE status='REJECTED' WHERE request_id=? AND status='PENDING'
    API-->>O: 200 (or 409 if not PENDING)
    R->>API: POST /resource-requests/{id}/cancel
    API->>API: caller == requested_by? else 403
    API->>DB: UPDATE status='CANCELLED' WHERE request_id=? AND status='PENDING'
    API-->>R: 200 (or 409). The row is kept for audit, and the pair can be requested again
```

## Resource request lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING : create (requester owns incident)
    [*] --> DEPLOYED : create with deployImmediately (requester also owns resource, resource free)
    PENDING --> DEPLOYED : deploy (owner, resource not deployed elsewhere)
    PENDING --> REJECTED : reject (owner)
    PENDING --> CANCELLED : cancel (requester)
    DEPLOYED --> RETURNED : return (requester or owner)
    REJECTED --> [*]
    CANCELLED --> [*]
    RETURNED --> [*]
    note right of DEPLOYED
        At most one DEPLOYED per resource (uq_request_one_deployment).
        At most one PENDING/DEPLOYED per resource+incident (uq_request_active_pair).
        Resource "available" = no DEPLOYED request.
    end note
```

## Report

```mermaid
sequenceDiagram
    participant SPA
    participant API as ReportService
    participant DB as MySQL
    SPA->>API: GET /api/v1/reports/resources-by-esf
    API->>DB: esf LEFT JOIN resource (owner = caller) LEFT JOIN resource_request (DEPLOYED) GROUP BY esf
    API-->>SPA: 200 {rows[15], totals}
```
