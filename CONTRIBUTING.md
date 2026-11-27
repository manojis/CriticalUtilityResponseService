# Contributing to ERMS

Thanks for helping. This guide covers the local setup, the workflow and the rules a change must follow before it can merge.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 (Temurin) | Maven is not needed; use the wrapper `backend/mvnw` |
| Node.js / npm | 24 LTS (`frontend/.nvmrc`) / 11 | `npm ci` with the committed `package-lock.json` |
| Docker | Rancher Desktop (moby engine) or Docker Desktop | Needed for Testcontainers, compose and the linters |
| kubectl, helm | from Rancher Desktop (`~/.rd/bin`) | Helm 4 (Helm 3.x also works with the scripts) |
| gh | latest | Only for the self-hosted runner setup |

Testcontainers on Rancher Desktop needs:

```bash
export DOCKER_HOST=unix://$HOME/.rd/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

`make be-test` sets these automatically when `~/.rd/docker.sock` exists.

## Workflow

1. Branch from `main`: `feat/<short-topic>`, `fix/<short-topic>`, or `docs/…`, `ci/…`, `chore/…`, `refactor/…`, `test/…`.
2. Commit with [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`, for example `feat(api): add incident search`, `fix(frontend): keep filters after deploy`, `ci: pin trivy-action`. Mark breaking changes with `!` (`feat(api)!: …`) and a `BREAKING CHANGE:` footer.
3. Open a pull request against `main` and fill in the template. CI must be green (see the required checks in [docs/CICD.md](docs/CICD.md#quality-gates-a-merge-needs-all-green)).
4. Squash-merge. The merge to `main` builds and publishes the images, and CD deploys them to the Rancher Desktop environment.

`main` is protected by rulesets ([docs/CICD.md](docs/CICD.md#branch-protection-rulesets)): no direct or force pushes, PRs only from the branch prefixes above (plus `agent/…` and `dependabot/…`), squash-merge only, and only the owner (`@manojis`) can approve and merge.

## Local commands

```bash
make help            # all targets
make be-test         # backend: Spotless check, unit + integration tests, JaCoCo gates
make be-format       # backend: apply google-java-format
make fe-test         # frontend: lint, format check, typecheck, tests with coverage
make fe-format       # frontend: Prettier write
make lint            # every linter, including helm/kubeconform, shellcheck, actionlint, hadolint
make up              # full stack with docker compose at http://localhost:3000
make deploy-local    # build + deploy to Rancher Desktop + helm test + smoke test
```

## Code style

- **Java**: google-java-format through Spotless (`make be-format`). Constructor injection only, SLF4J logging (no `System.out`/`printStackTrace`), `java.time` types in APIs.
- **TypeScript/React**: ESLint flat config + Prettier (`make fe-format`), strict TypeScript, no `console.log`, no hardcoded hosts (the API base is the relative `/api`).
- **Shell**: `set -euo pipefail`, shellcheck-clean (`make sh-lint`).
- **Workflows**: actionlint-clean (`make actions-lint`). Pin every third-party action to a full commit SHA with a version comment.
- **Helm**: `make helm-lint` must pass (helm lint `--strict` + kubeconform `-strict`).
- **Editor**: honour `.editorconfig`.

## Pull request checklist

- [ ] Tests added or updated, and the relevant `make` targets pass locally.
- [ ] Docs updated in the same PR (API.md, DATA_MODEL.md, DEPLOYMENT.md, RUNBOOK.md, CICD.md, an ADR when a decision changes).
- [ ] `CHANGELOG.md` `[Unreleased]` updated for user-visible changes.
- [ ] No secrets or credentials committed. Only the labelled `change-me-…` dev placeholders in `docker-compose.yml` are allowed.
- [ ] No workflow triggered by `pull_request`/`pull_request_target` uses `runs-on: self-hosted` (the repository is public).
- [ ] Database migrations are new files (never edit an applied migration) and backward-compatible (expand/contract).

## Architecture decisions

Significant decisions are recorded as ADRs in [docs/adr/](docs/adr/). Copy the format of an existing ADR, use the next number (`NNNN-short-title.md`), start with `Status: Proposed`, and change it to `Accepted` (or `Superseded by NNNN`) when the PR merges. Add it to the index in [docs/README.md](docs/README.md).

## Reporting security issues

Don't open public issues for vulnerabilities. Use a private GitHub security advisory (Security → Advisories → Report a vulnerability). See [docs/SECURITY.md](docs/SECURITY.md#reporting-a-vulnerability).
