# ADR-0007: CI on GitHub-hosted runners, CD to Rancher Desktop via a self-hosted runner
Status: Accepted · 2026-11-25

## Context
The deploy target is a k3s cluster on a developer Mac. GitHub-hosted runners can't reach it, and the repo is **public**.

## Decision
- `ci.yml` runs on ubuntu-latest: tests, lint, helm/kubeconform, hadolint, Trivy and CodeQL. On push to main it builds multi-arch (amd64+arm64) images and pushes them to GHCR tagged with the git sha and `latest`, with SBOM and provenance.
- `cd.yml` is triggered only by `workflow_run` of a successful CI **push** on main from this repo, or manually. It runs on `[self-hosted, macOS, ARM64, rancher-desktop]` under environment `local`, pulls the sha images into Rancher Desktop's moby store (shared with k3s), runs `helm upgrade --install --rollback-on-failure` (Helm 4; formerly `--atomic` --wait`, then helm test and a smoke test.
- No `pull_request` workflow ever targets the self-hosted runner. Fork-PR workflows require approval. The environment is restricted to `main`.

## Alternatives
- actions-runner-controller in-cluster: needs a PAT or App and adds more moving parts.
- Pull-based GitOps (Argo CD/Flux): an excellent next step, but it doesn't fit the "GitHub Actions deploys" requirement as directly.

## Consequences
Deploys only happen while the Mac and Rancher Desktop are running. Jobs queue until then.
