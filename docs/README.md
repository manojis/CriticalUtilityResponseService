# ERMS Documentation

| Document | What's in it | Owner |
|---|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Target architecture, tech stack, package layout, config, platform design, legacy defect list (§6), OWASP mapping | Architect |
| [FEATURES.md](FEATURES.md) | Functional features: user stories, acceptance criteria, business rules, UI notes, NFRs | BE (rules), FE (UI notes) |
| [FLOWS.md](FLOWS.md) | Mermaid sequence diagrams (login/JWT, incident, resource, search, request lifecycle) and the request state diagram | Architect / BE |
| [API.md](API.md) | REST API v1 contract: auth, error model, every endpoint, examples, legacy mapping | BE |
| [DATA_MODEL.md](DATA_MODEL.md) | ER diagram, data dictionary, derived values, reference and demo data | BE |
| [SECURITY.md](SECURITY.md) | Threat model, trust boundaries, OWASP mapping, secrets handling, hardening | Architect / PLAT |
| [TESTING.md](TESTING.md) | Test pyramid, per-layer coverage, coverage gates, how to run | All |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Environments, k8s topology, images, Helm values, deploy paths, rollback | PLAT |
| [CICD.md](CICD.md) | Pipeline diagram, workflows, quality gates, self-hosted runner, public-repo safeguards | PLAT |
| [RUNBOOK.md](RUNBOOK.md) | Operations: triage, procedures, known failure modes | PLAT |
| [WORK_PACKAGES.md](WORK_PACKAGES.md) | Work split, file ownership, acceptance criteria, QA checklist | Architect |
| [adr/](adr/) | Architecture Decision Records 0001–0008 | Architect |

Root files: [README.md](../README.md) (quick start), [CONTRIBUTING.md](../CONTRIBUTING.md), [CHANGELOG.md](../CHANGELOG.md).

## ADR index
| # | Decision |
|---|---|
| [0001](adr/0001-monorepo-layout.md) | Monorepo layout (`backend/`, `frontend/`, `deploy/`) |
| [0002](adr/0002-spring-boot-4-jdbc.md) | Java 21 + Spring Boot 4.1 with JdbcTemplate (no JPA) |
| [0003](adr/0003-stateless-jwt-auth.md) | Stateless JWT (HS256) via Spring Security resource server |
| [0004](adr/0004-schema-and-request-state-machine.md) | Schema redesign, server-generated incident IDs, request state machine, derived availability |
| [0005](adr/0005-frontend-vite-react-ts-mui.md) | Vite + React 19 + TypeScript + MUI 9 |
| [0006](adr/0006-helm-chart-in-chart-mysql.md) | Own Helm chart with in-chart MySQL StatefulSet |
| [0007](adr/0007-ci-github-hosted-cd-self-hosted.md) | CI on hosted runners, CD via a self-hosted runner to Rancher Desktop |
| [0008](adr/0008-observability-and-management-port.md) | Separate management port, probes, metrics, structured logs |

## Documentation rules
- Docs change in the **same PR** as the behaviour they describe. QA checks that docs match the implementation.
- Diagrams are Mermaid (they render on GitHub). Don't commit binary diagrams.
- Record significant decisions as new ADRs (`adr/NNNN-title.md`, status Proposed → Accepted/Superseded).
