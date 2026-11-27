## What and why
<!-- Summary of the change and the motivation. Link issues (Fixes #123) and legacy defect IDs (e.g. F12, S3). -->

## How it was tested
<!-- Commands you ran and their result, e.g. `make be-test`, `make fe-test`, `make helm-lint`, `make smoke`. -->

## Checklist
- [ ] Conventional Commit title (`feat: …`, `fix: …`, `docs: …`, `ci: …`, `build: …`, `refactor: …`, `test: …`, `chore: …`)
- [ ] Tests added or updated; `make lint` and the relevant test targets pass locally
- [ ] Docs updated in this PR (API.md / DATA_MODEL.md / DEPLOYMENT.md / RUNBOOK.md / CICD.md / ADR as applicable)
- [ ] `CHANGELOG.md` `[Unreleased]` updated for user-visible changes
- [ ] No secrets, credentials or personal data committed (dev placeholders only, clearly labelled)
- [ ] Workflows: no `pull_request`/`pull_request_target` workflow uses `runs-on: self-hosted`; new actions are pinned to a full commit SHA
- [ ] DB migrations are forward-only and backward-compatible (expand/contract)
