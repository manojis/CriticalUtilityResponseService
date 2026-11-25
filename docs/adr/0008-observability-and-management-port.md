# ADR-0008: Separate management port, probes, metrics, structured logs
Status: Accepted · 2026-11-25

## Decision
Actuator runs on port 8081 (health/info/prometheus), which is never routed by the Ingress. Kubernetes startup/liveness/readiness probes use the Boot health groups, and readiness includes the DB. Logs are ECS-format JSON on stdout with an MDC `requestId` (echoed as `X-Request-Id`). Prometheus scrape annotations are set on the pod.

## Consequences
External health checks go through the API (smoke test) or in-cluster `helm test`.
