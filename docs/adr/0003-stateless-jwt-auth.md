# ADR-0003: Stateless JWT authentication (HS256) via Spring Security resource server
Status: Accepted · 2026-11-25

## Context
The legacy API had no auth. The client sent `username` on every call (IDOR), and passwords were stored in plaintext.

## Decision
`POST /api/v1/auth/login` verifies a BCrypt hash and issues an HS256 JWT (1h TTL) using Nimbus via `spring-boot-starter-oauth2-resource-server`. All other `/api/**` endpoints require a Bearer token. Identity always comes from `sub`. The secret comes from env/K8s Secret and must be at least 32 bytes.

## Alternatives
- Session cookies: need CSRF protection and sticky or shared sessions.
- External IdP (Keycloak): the right long-term choice, but too heavy for the local scope. The resource-server setup lets us switch to RS256/JWKS later with a config change.

## Consequences
No server-side logout; tokens are short-lived instead. The SPA keeps the token in sessionStorage, which is mitigated by a strict CSP. Refresh tokens are out of scope.
