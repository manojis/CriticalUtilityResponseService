# ERMS Functional Features

Each feature lists user stories, acceptance criteria (AC) and business rules (BR). API details are in [API.md](API.md), flows in [FLOWS.md](FLOWS.md), and the schema in [DATA_MODEL.md](DATA_MODEL.md). "UI notes" are owned by the frontend developer (WP-FE). Everything else is owned by the backend developer (WP-BE).

## Actors and user types
All four user types have the **same permissions**. The type only changes the profile details shown. Authorization is per record (owner/requester), not per role.

| userType | Profile fields | Main-menu subtitle |
|---|---|---|
| INDIVIDUAL | jobTitle, hireDate | jobTitle |
| MUNICIPALITY | municipalityCategory | municipalityCategory |
| GOVERNMENT_AGENCY | agencyNameLocalOffice | agencyNameLocalOffice |
| COMPANY | headquarters, numberOfEmployees | headquarters |

User accounts are provisioned in the database (seeded in the `demo` profile). Self-registration and user management are out of scope for v1.

---

## F-1 Authentication
**Stories**
- As a user, I log in with username and password so I can use ERMS.
- As a user, I'm sent back to login when my session expires, and I log out explicitly when I'm done.

**AC**
1. Valid credentials → token issued, redirect to the main menu (or the originally requested page).
2. Invalid username or password → the same generic error, no hint about which part was wrong, HTTP 401.
3. Any page other than `/login` without a valid token redirects to `/login`.
4. An expired token on any API call → automatic logout and redirect with the message "Session expired".
5. Logout clears the stored token and returns to `/login`.

**BR** Passwords are stored as BCrypt hashes only. The token TTL defaults to 1 h. Identity comes only from the token.

## F-2 Profile and main menu
**Story** As a user, I see my name and type-specific details, plus navigation to every function.

**AC** `GET /me` populates the header. The subtitle follows the table above. The menu links to: Add Resource, Add Incident, Search Resources, Resource Status, Resource Report, Logout.

## F-3 Add incident
**Story** As a user responding to an emergency, I declare an incident so I can request resources for it.

**AC**
1. The form has: declaration (select), incident date, description, latitude, longitude.
2. On save, the server assigns the ID `<ABBR>-<n>`, which the UI shows. The user never types an ID.
3. Concurrent creates never produce duplicate IDs.
4. Invalid input shows a message per field (client and server).

**BR** date ≤ today · description 6–500 chars · latitude −90..90 · longitude −180..180 · declaration must exist · owner = caller · IDs are sequential per declaration and never reused.

**UI notes** Declarations are sorted by name. The date picker blocks future dates. A success message shows the new ID with a link to Search for that incident.

## F-4 Add resource
**Story** As a resource owner, I register equipment or services with their capabilities, cost and location so responders can find them.

**AC**
1. Owner is shown (from `/me`) and isn't editable.
2. Fields: name, model (optional), primary ESF, additional ESFs (multi-select, excludes the primary), capabilities (add/remove list), cost + unit, latitude, longitude, max distance km (optional).
3. Save is atomic: the resource, its capabilities and its ESFs are all saved, or nothing is.
4. Success shows the new resource ID.

**BR** name 3–100 · cost ≥ 0 with 2 decimals · unit and ESFs must exist · additional ESFs unique and ≠ primary · capabilities trimmed, deduplicated case-insensitively, 1–100 chars each · max distance > 0 or empty (empty = no limit) · a new resource is available.

**UI notes** Changing the primary ESF removes it from the additional ESFs. Capabilities show as chips.

## F-5 Search resources
**Stories**
- As a responder, I search resources by keyword and/or ESF.
- As a responder with an incident, I find resources within N km of it, nearest first.

**AC**
1. All filters are optional and combine with AND. With no filters, every resource is listed (paginated).
2. Keyword matches name, model or any capability, case-insensitively, as a substring. `%` and `_` are treated literally.
3. ESF matches the primary ESF or any additional ESF.
4. With an incident selected, every row shows its distance (km, 1 decimal), sorted nearest first. Resources outside their own max distance are excluded. With "within N km", rows beyond N are excluded.
5. "Within N km" can't be used without an incident.
6. Each row shows: ID, name, owner, cost/unit, status ("Available" or "In use until YYYY-MM-DD"), and distance (if any).
7. Each resource appears at most once.

**BR** Only the caller's own incidents can be used as the search origin. Search covers all users' resources.

**UI notes** Action column (only when an incident is selected): **Deploy** for my own available resources; **Request** otherwise. Both open a dialog asking for the expected return date (≥ today). The table is paginated (20/page).

## F-6 Resource request lifecycle
**Stories**
- As a responder, I request someone's resource for my incident and can cancel while it's pending.
- As an owner, I deploy or reject requests for my resources.
- As a responder (or the owner), I mark a deployed resource as returned.

**AC**
1. Request → status PENDING, request date = today.
2. Owner deploy → DEPLOYED, deploy date = today. Not possible while the resource is deployed elsewhere.
3. Owner reject → REJECTED. Requester cancel → CANCELLED. Both only from PENDING.
4. Return → RETURNED, actual return date = today, and the resource becomes available. Only that request changes.
5. Illegal actions are refused with a clear message (409). Acting on someone else's request is refused (403/404).
6. After reject, cancel or return, the same resource can be requested again for the same incident.
7. Deploying my own resource to my own incident creates it directly as DEPLOYED.

**BR** See the state machine in [FLOWS.md](FLOWS.md#resource-request-lifecycle). Expected return date ≥ request date. History is never deleted.

## F-7 Resource status dashboard
**Story** As a user, I see in one place what I'm using, what I've asked for, and what others have asked of me.

**AC** Three tables, each with an empty state:
| Table | Source | Columns | Actions |
|---|---|---|---|
| Resources in use | `role=requester&status=DEPLOYED` | ID, resource, incident, owner, deploy date, expected return | Return |
| Resources requested by me | `role=requester&status=PENDING` | ID, resource, incident, owner, expected return | Cancel |
| Requests received | `role=owner&status=PENDING` | ID, resource, incident, requested by, expected return | Deploy (disabled unless resource available), Reject |

All tables refresh after any action, and a success or error snackbar is shown.

## F-8 Resource report
**Story** As an owner, I see my resources by primary ESF and how many are in use.

**AC** All 15 ESFs are listed (zero rows included) with ESF #, description, total resources and resources in use, plus a totals row. The numbers update immediately after deploy and return.

## Non-functional requirements
| Area | Requirement |
|---|---|
| Security | OWASP Top 10 baseline ([SECURITY.md](SECURITY.md)); no secrets in git |
| Performance | p95 < 300 ms for search with ≤ 10k resources (single query, indexed) |
| Availability | Kubernetes probes; graceful shutdown; rolling update with no downtime when replicas > 1 |
| Observability | JSON logs with requestId; Prometheus metrics; health endpoints |
| Accessibility | Labelled form controls, keyboard navigable, MUI defaults (WCAG 2.1 AA intent) |
| Browser support | Last 2 versions of evergreen browsers |
