# ADR-0001: Monorepo layout
Status: Accepted · 2026-11-25

## Context
The repo holds `erms/` (Spring Boot) and `front-end/` (CRA). The names are inconsistent, and there's no infra, CI or docs.

## Decision
Keep one repository. Rename to `backend/` and `frontend/` with `git mv` so history is preserved. Add `deploy/helm/erms`, `.github/`, `scripts/`, `docs/`, a root `Makefile` and `docker-compose.yml`. Each component owns its Dockerfile.

## Consequences
One PR can change the API and the UI atomically. CI uses per-path jobs. Version tags apply to the whole system.
