# ERMS CI/CD

Owner: platform developer (WP-PLAT). Design rationale: [ADR-0007](adr/0007-ci-github-hosted-cd-self-hosted.md).

## Pipeline overview

```mermaid
flowchart LR
    subgraph PR["Pull request → main"]
        direction TB
        P1[backend: mvnw verify] --- P2[frontend: lint/type/test/build]
        P2 --- P3[helm lint + kubeconform]
        P3 --- P4[hadolint + actionlint + shellcheck]
        P4 --- P5[Trivy fs + SARIF]
        P5 --- P6[images: build amd64, no push, Trivy image]
        P7[CodeQL java + ts]
    end
    subgraph MAIN["Push to main"]
        direction TB
        M1[same quality jobs] --> M2[images: buildx amd64+arm64<br/>push GHCR :sha :latest<br/>SBOM + provenance<br/>Trivy image]
    end
    subgraph CD["CD (workflow_run: CI success on main push)"]
        direction TB
        D1["self-hosted runner on Mac<br/>labels: self-hosted, macOS, ARM64, rancher-desktop"] --> D2[docker pull :sha into Rancher Desktop]
        D2 --> D3["helm upgrade --install --rollback-on-failure --wait"]
        D3 --> D4[helm test]
        D4 --> D5["smoke-test.sh http://erms.localhost"]
    end
    PR -->|merge| MAIN -->|CI success| CD
```

## Workflows
| File | Name | Triggers | Runners | Permissions |
|---|---|---|---|---|
| `.github/workflows/ci.yml` | CI | `pull_request` (main), `push` (main), `workflow_dispatch` | ubuntu-latest | top level `contents: read`; `security-events: write` on `security`; `packages: write` + `security-events: write` on `images` |
| `.github/workflows/cd.yml` | CD | `workflow_run` (CI completed on main), `workflow_dispatch` (`image_tag`, main only) | `[self-hosted, macOS, ARM64, rancher-desktop]` | `contents: read`, `packages: read` |
| `.github/workflows/codeql.yml` | CodeQL | push/PR to main, weekly (Mon 04:27 UTC), `workflow_dispatch` | ubuntu-latest | `contents: read`, `security-events: write` |
| `.github/dependabot.yml` | – | weekly (Monday): maven, npm, docker, docker-compose, helm, github-actions; minor/patch grouped | – | – |

CI jobs: `backend` (`./mvnw -B -ntp verify` + reports), `frontend` (`npm ci`, lint, format:check, typecheck, test:coverage, build), `helm` (helm lint `--strict` with local and default values, kubeconform `-strict` against Kubernetes 1.36.0 for the local values and a production-style render), `dockerfiles` (hadolint, threshold `warning`), `infra-lint` (actionlint, shellcheck, and a guard that fails if any workflow combines `self-hosted` with a `pull_request` trigger), `security` (Trivy fs: vuln + secret + misconfig, SARIF upload, fails on CRITICAL), `images` (matrix backend/frontend; see below), `ci-result` (aggregates everything into one required check).

The `images` job builds `linux/amd64` + `linux/arm64` with buildx and pushes `ghcr.io/manojis/erms-{backend,frontend}:<sha>` and `:latest` with SBOM and provenance attestations **only on push to main**, then Trivy-scans the pushed sha (SARIF + gate on fixable CRITICAL). On pull requests and manual runs it builds `linux/amd64` only (`push: false`, `load: true`) and Trivy-scans the local image.

Every third-party action is pinned to a full commit SHA with a `# vX.Y.Z` comment (the SHAs were resolved with `gh api repos/<owner>/<repo>/git/ref/tags/<tag>`, dereferencing annotated tags). Dependabot updates both the SHA and the comment. Tool images are pinned by version in the workflow `env` (`HELM_VERSION`, `KUBECONFORM_IMAGE`, `ACTIONLINT_IMAGE`, `TRIVY_VERSION`). Job specifications: [ARCHITECTURE.md §5.3–5.4](ARCHITECTURE.md#53-ci-githubworkflowsciyml-name-ci).

## Quality gates (a merge needs all green)
backend tests + coverage · frontend lint/typecheck/tests/coverage/build · helm lint + kubeconform · hadolint · Trivy (no CRITICAL) · CodeQL (no new high alerts) · image build.

Required status checks for `main` (check names as GitHub displays them):

| Check | Workflow |
|---|---|
| `CI result` | CI (aggregates `backend (mvnw verify)`, `frontend (lint, typecheck, test, build)`, `helm lint + kubeconform`, `hadolint`, `actionlint + shellcheck`, `Trivy filesystem scan`, `image backend`, `image frontend`) |
| `analyze (java-kotlin)`, `analyze (javascript-typescript)`, `analyze (actions)` | CodeQL |
| `Source branch` | Branch policy (`.github/workflows/branch-policy.yml`) |

### Branch protection (rulesets)
`main` (the default branch, targeted as `~DEFAULT_BRANCH`) is protected by two repository rulesets (Settings → Rules → Rulesets). Branch-protection rules are not used.

| Ruleset | Bypass | Rules |
|---|---|---|
| `main: protection` | **nobody** (not even admins) | PR required (0 approvals; conversations resolved; stale approvals dismissed), squash only; required checks `Source branch`, `CI result`, `analyze (java-kotlin)`, `analyze (javascript-typescript)`, `analyze (actions)` (strict, branch up to date); linear history; no force push; no deletion |
| `main: code-owner review` | Repository admin, **only through a PR** (`bypass_mode: pull_request`) | PR needs 1 approval from a code owner (`.github/CODEOWNERS`: `* @manojis`), approval after the last push |

Effect:
- Nobody can push to `main` directly; every change is a PR.
- `Source branch` allows only `feat/`, `fix/`, `docs/`, `ci/`, `chore/`, `refactor/`, `test/`, `agent/` and `dependabot/` heads. No one can bypass it.
- GitHub never lets authors approve their own PRs, so the owner merges their own PRs with **Merge without waiting for requirements** (`gh pr merge --squash --admin`). This bypasses only the code-owner ruleset. Everyone else needs `@manojis`'s approval.
- Only users with write access can merge, and `@manojis` is the only collaborator.
- Repository settings: squash merge only, auto-merge allowed, head branches deleted after merge.

Inspect: `gh api repos/manojis/CriticalUtilityResponseService/rulesets`. To change the required checks, edit the ruleset in the UI or `gh api -X PUT repos/manojis/CriticalUtilityResponseService/rulesets/<id>` with the full rule list. A new check name only becomes selectable after it has run once.

## Environments
| GitHub Environment | Used by | Protection |
|---|---|---|
| `local` | `cd.yml` → Rancher Desktop | Deployment branches: `main` only. Optional required reviewer |

## Self-hosted runner (Rancher Desktop host)
- Installed by `scripts/setup-local-runner.sh` into `~/actions-runner/erms`, running as a **LaunchAgent** under the user's account (so it can use `~/.rd/docker.sock` and `~/.kube/config`).
- Labels: `self-hosted`, `macOS`, `ARM64` (automatic) + `rancher-desktop`.
- `.path` includes `~/.rd/bin` so `docker`, `kubectl` and `helm` resolve.
- Jobs queue while the Mac sleeps or Rancher Desktop is stopped, and run once it's back.


Install (idempotent; re-run it any time to repair):
```bash
gh auth login                      # admin on manojis/CriticalUtilityResponseService
make runner-setup                  # = scripts/setup-local-runner.sh [--repo owner/name] [--name NAME]
```
What it does, in order: preflight (macOS arm64, `gh auth status` + repo admin, `docker info`, `kubectl --context rancher-desktop get nodes`, `helm`, `rdctl` Kubernetes check) → downloads the latest `actions/runner` osx-arm64 tarball and verifies its SHA-256 against the hash in the release notes (cross-checked with GitHub's asset digest) → registers with `config.sh --unattended --replace --labels rancher-desktop --name <LocalHostName>-rancher-desktop` using a short-lived registration token from `gh api -X POST repos/…/actions/runners/registration-token` (skipped if already registered) → writes `.path`/`.env` → `./svc.sh install && ./svc.sh start` (LaunchAgent) → hardening (below). `--skip-hardening` skips the repository settings.

Operate:
```bash
cd ~/actions-runner/erms
./svc.sh status                                   # loaded + PID
./svc.sh stop && ./svc.sh start                   # restart
ls ~/Library/Logs/actions.runner.manojis-CriticalUtilityResponseService.*/   # stdout.log, stderr.log
tail -n 100 _diag/Runner_*.log                    # runner diagnostics; job logs: _diag/Worker_*.log
gh api repos/manojis/CriticalUtilityResponseService/actions/runners --jq '.runners[] | {name, status, busy}'
```

Uninstall:
```bash
make runner-uninstall              # = scripts/setup-local-runner.sh --uninstall (stops/removes the LaunchAgent, unregisters)
rm -rf ~/actions-runner/erms       # optional: delete the files
```

The CD job uses a job-scoped `DOCKER_CONFIG` under `$RUNNER_TEMP` and `DOCKER_HOST=unix://$HOME/.rd/docker.sock`, so the GHCR login never touches your own `~/.docker/config.json`. It logs out and deletes that directory at the end of every run.

### Public-repository safeguards (mandatory)
1. Only `cd.yml` targets the self-hosted runner. Its triggers are `workflow_run` (with `event == 'push'` and the head repository equal to this repo) and `workflow_dispatch` (write access required).
2. No workflow with a `pull_request` or `pull_request_target` trigger may use `runs-on: self-hosted`. QA verifies this, and reviewers must reject PRs that add it.
3. Fork-PR workflows require maintainer approval for **all external contributors** (`fork-pr-contributor-approval` = `all_external_contributors`, applied by the setup script). The script also sets the default `GITHUB_TOKEN` to read-only, stops workflows from approving PRs, and enables secret scanning with push protection.
4. Environment `local` only accepts deployments from `main` (custom deployment branch policy, created by the setup script).
5. The CD job checks out the exact `head_sha` that CI validated and deploys images by that sha. A manual `image_tag` is validated against `^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$` and passed through `env`, never interpolated into scripts.
6. CI enforces rule 2 automatically (the `infra-lint` job's self-hosted guard). Checkouts use `persist-credentials: false`.

## Image tagging & promotion
`<git-sha>` (immutable, deployed) and `latest` (convenience only, never deployed by CD). To promote or roll back, redeploy a sha via `workflow_dispatch` (see [DEPLOYMENT.md → Rollback](DEPLOYMENT.md#rollback)).

## Secrets & tokens
No repository secrets are needed. CI and CD use the job-scoped `GITHUB_TOKEN` for GHCR. Packages pushed by CI are linked to this repository through the `org.opencontainers.image.source` label, which gives the repository's workflows read access for CD. Kubernetes secrets are generated in-cluster by Helm ([SECURITY.md](SECURITY.md#secrets-handling)).

## Troubleshooting
| Symptom | Likely cause | Action |
|---|---|---|
| CD job queued forever | Runner offline (Mac asleep, Rancher Desktop stopped) | Start Rancher Desktop; check the runner service status |
| `docker: command not found` in CD | Runner PATH | Re-run the setup script (rewrites `.path`) |
| `helm upgrade` timed out → rolled back | Pod not ready (DB, image, probe) | See [RUNBOOK.md](RUNBOOK.md) diagnostics; the CD job dumps pod events and logs on failure |
| `manifest unknown` on pull | Images job skipped or failed | Check the CI run's images job for that sha |
| `Flag --atomic has been deprecated` | Helm 4 renamed the flag | Use `--rollback-on-failure` (scripts and CD detect the Helm major version) |
| CD `docker pull` → `denied` | The GHCR package is not linked to the repo / Actions access missing | Package settings → Manage Actions access → add this repository (Read). Check that the image has the `org.opencontainers.image.source` label |
| `upload-sarif` fails on a fork PR | Fork PR tokens are read-only | Expected; the upload step is skipped for fork PRs |
| actionlint: `label "rancher-desktop" is unknown` | Custom runner label | Declared in `.github/actionlint.yaml` |
| `$K get pods` fails in zsh with "command not found" | zsh doesn't word-split variables | Use the `k()` function from [RUNBOOK.md](RUNBOOK.md) |
| `kubectl run` rejected: `violates PodSecurity "restricted"` | Namespace `erms` enforces Pod Security `restricted` | Give the pod a restricted securityContext (RUNBOOK procedure 13) |
| CodeQL: "default setup is enabled" error | Repository default setup conflicts with `codeql.yml` | Settings → Code security → CodeQL → switch to Advanced (currently not configured) |
