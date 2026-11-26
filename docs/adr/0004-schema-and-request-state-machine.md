# ADR-0004: Schema redesign, server-generated incident IDs, request state machine with derived availability
Status: Accepted · 2026-11-25

## Context
No schema was committed. The legacy code kept `Resource.is_available/next_available` in sync with `RequestedBy` by hand and got it wrong (F12–F18). Incident IDs used client-side COUNT+1 (F8).

## Decision
- Flyway `V1` schema with snake_case tables, FKs, CHECKs and indexes (ARCHITECTURE §3.9).
- `resource_request.status` enum (PENDING/DEPLOYED/REJECTED/CANCELLED/RETURNED) driven by a server-side state machine. Rows are never deleted.
- Availability is **derived**: a resource is in use iff a DEPLOYED request exists. Stored generated columns plus UNIQUE keys enforce at most one active request per (resource, incident) and at most one deployment per resource.
- Incident IDs `<ABBR>-<n>` come from `incident_sequence` under `SELECT … FOR UPDATE`.

## Consequences
Business invariants hold even with concurrent requests. The DB is a clean break; there's no legacy data to migrate.
