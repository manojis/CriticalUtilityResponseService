#!/usr/bin/env bash
# Autonomous design → implement → test loop between two Copilot CLI agents.
#
#   IMPLEMENTER (default claude-opus-5.5): designs the change, implements it, writes unit and
#                functional tests and runs them.
#   VERIFY      (this script, no LLM):      runs the real test suites ($VERIFY_CMD).
#   TESTER      (default gpt-6.2-sol, must not be a Claude model): independent QA. Reviews the
#                change against the task and design, runs the tests, adds tests that expose
#                defects (test files only), and writes a PASS/FAIL verdict.
#
# The tester's report and any verification failures are fed back to the implementer, and the cycle
# repeats until verification passes AND the tester says PASS, or --max-iterations is reached.
# Nothing asks for input. All work happens on a new branch in a separate git worktree, so your
# checkout, index and uncommitted files are never touched.
#
# Usage:
#   scripts/agent-loop.sh --task "Add …"            [options]
#   scripts/agent-loop.sh --task-file feature.md    [options]
#
# Options:
#   --task TEXT | --task-file PATH   What to build (required)
#   --max-iterations N               Implement/test rounds before giving up (default 5)
#   --impl-model MODEL               Implementer model (default claude-opus-5.5)
#   --test-model MODEL               Tester model, non-Claude (default gpt-6.2-sol)
#   --base snapshot|REF              Start point. "snapshot" (default) = the current working tree,
#                                    including uncommitted and untracked files; or any git ref
#   --verify-cmd CMD                 Deterministic gate run after each round (default "make be-test fe-test")
#   --pr                             On success push the branch and open a draft PR (needs --base REF)
#   --run-dir DIR                    Where runs are stored (default ~/.cache/erms-agent-loop)
#   -h, --help                       Show this help
#
# Exit codes: 0 converged, 1 not converged / agent failure, 2 usage error.
# Env: AGENT_CMD overrides the agent binary (default "copilot"); used for testing the loop itself.
set -euo pipefail

usage() { sed -n '2,/^set -euo/p' "$0" | sed -e '$d' -e 's/^# \{0,1\}//'; }
die() { echo "agent-loop: $*" >&2; exit 2; }
log() { printf '\n\033[1m[agent-loop %s] %s\033[0m\n' "$(date +%H:%M:%S)" "$*"; }

TASK=""
MAX_ITER=5
IMPL_MODEL="claude-opus-5.5"
TEST_MODEL="gpt-6.2-sol"
BASE="snapshot"
VERIFY_CMD="make be-test fe-test"
OPEN_PR=false
RUN_ROOT="${HOME}/.cache/erms-agent-loop"
AGENT_CMD="${AGENT_CMD:-copilot}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --task) TASK="${2:?--task needs a value}"; shift 2 ;;
    --task-file) [[ -f "${2:-}" ]] || die "task file not found: ${2:-}"; TASK="$(cat "$2")"; shift 2 ;;
    --max-iterations) MAX_ITER="${2:?}"; shift 2 ;;
    --impl-model) IMPL_MODEL="${2:?}"; shift 2 ;;
    --test-model) TEST_MODEL="${2:?}"; shift 2 ;;
    --base) BASE="${2:?}"; shift 2 ;;
    --verify-cmd) VERIFY_CMD="${2:?}"; shift 2 ;;
    --pr) OPEN_PR=true; shift ;;
    --run-dir) RUN_ROOT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1 (see --help)" ;;
  esac
done

[[ -n "${TASK//[[:space:]]/}" ]] || die "--task or --task-file is required"
[[ "$MAX_ITER" =~ ^[1-9][0-9]*$ ]] || die "--max-iterations must be a positive integer"
shopt -s nocasematch
[[ "$TEST_MODEL" != *claude* ]] || die "--test-model must not be a Claude model (got $TEST_MODEL)"
shopt -u nocasematch
if $OPEN_PR && [[ "$BASE" == "snapshot" ]]; then
  die "--pr needs --base <ref>: a snapshot base contains uncommitted work that is not on the remote"
fi
command -v "$AGENT_CMD" >/dev/null || die "agent command not found: $AGENT_CMD"

REPO="$(git rev-parse --show-toplevel 2>/dev/null)" || die "run inside the ERMS git repository"
cd "$REPO"

# Testcontainers on Rancher Desktop, inherited by the agents' own mvnw runs (docs/TESTING.md).
if [[ -S "$HOME/.rd/docker.sock" && -z "${DOCKER_HOST:-}" ]]; then
  export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
fi

RUN_ID="$(date +%Y%m%d-%H%M%S)"
SLUG="$(printf '%s' "$TASK" | head -n1 | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | cut -c1-40 | sed 's/^-*//;s/-*$//')"
BRANCH="agent/${SLUG:-task}-${RUN_ID}"
RUN_DIR="${RUN_ROOT}/${RUN_ID}"
WT="${RUN_DIR}/worktree"
mkdir -p "$RUN_DIR"
printf '%s\n' "$TASK" >"$RUN_DIR/task.md"

# --- Base commit -------------------------------------------------------------------------------
if [[ "$BASE" == "snapshot" ]]; then
  # Commit the whole working tree (tracked + untracked, .gitignore respected) through a temporary
  # index, without touching the real index, HEAD or files.
  tmp_index="$(mktemp)"
  trap 'rm -f "$tmp_index"' EXIT
  GIT_INDEX_FILE="$tmp_index" git read-tree HEAD
  GIT_INDEX_FILE="$tmp_index" git add -A
  tree="$(GIT_INDEX_FILE="$tmp_index" git write-tree)"
  BASE_COMMIT="$(git commit-tree "$tree" -p HEAD -m "agent-loop: snapshot of working tree ($RUN_ID)")"
  BASE_LABEL="working-tree snapshot of $(git branch --show-current 2>/dev/null || echo HEAD)"
else
  BASE_COMMIT="$(git rev-parse --verify "${BASE}^{commit}")" || die "unknown ref: $BASE"
  BASE_LABEL="$BASE"
fi

log "Run $RUN_ID: branch $BRANCH from $BASE_LABEL (${BASE_COMMIT:0:12})"
git worktree add -q -b "$BRANCH" "$WT" "$BASE_COMMIT"
git -C "$WT" config user.name >/dev/null || git -C "$WT" config user.name "agent-loop"
git -C "$WT" config user.email >/dev/null || git -C "$WT" config user.email "agent-loop@localhost"

if [[ -f "$WT/frontend/package-lock.json" ]]; then
  log "Installing frontend dependencies in the worktree"
  (cd "$WT/frontend" && npm ci --no-audit --no-fund --loglevel=error) >"$RUN_DIR/npm-ci.log" 2>&1 \
    || echo "npm ci failed (see $RUN_DIR/npm-ci.log); agents will retry via make fe-install"
fi

# --- Helpers -----------------------------------------------------------------------------------
# Commands no agent may run. Commits, branches and deployments belong to this script.
DENY=(
  'shell(git push)' 'shell(git commit)' 'shell(git reset)' 'shell(git checkout)'
  'shell(git switch)' 'shell(git rebase)' 'shell(git merge)' 'shell(git worktree)'
  'shell(git stash)' 'shell(git clean)' 'shell(gh)' 'shell(kubectl)' 'shell(helm)'
  'shell(scripts/deploy-local.sh)' 'shell(scripts/setup-local-runner.sh)'
)

run_agent() { # role model prompt_file iter_dir
  local role="$1" model="$2" prompt_file="$3" dir="$4" args=() rule attempt
  for rule in "${DENY[@]}"; do args+=(--deny-tool "$rule"); done
  for attempt in 1 2; do
    log "$role ($model), attempt $attempt"
    if (cd "$WT" && "$AGENT_CMD" -p "$(cat "$prompt_file")" --model "$model" \
          --allow-all --no-ask-user --silent "${args[@]}" \
          --usage-output-file "$dir/usage-$role.json") >"$dir/$role.log" 2>&1; then
      return 0
    fi
    echo "$role agent exited non-zero (log: $dir/$role.log)"
  done
  return 1
}

commit_all() { # message
  git -C "$WT" add -A
  if git -C "$WT" diff --cached --quiet; then return 1; fi
  git -C "$WT" commit -q --no-verify -m "$1

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
}

verify() { # log_file → exit status of $VERIFY_CMD
  log "Verify: $VERIFY_CMD"
  (cd "$WT" && bash -c "$VERIFY_CMD") >"$1" 2>&1
}

is_test_path() {
  [[ "$1" == backend/src/test/* || "$1" == frontend/src/test/* || "$1" =~ ^frontend/src/.*\.test\.tsx?$ ]]
}

# Reverts every change the tester made outside test files; prints the reverted paths.
enforce_tester_scope() {
  local status path
  while IFS= read -r -d '' entry; do
    status="${entry:0:2}"; path="${entry:3}"
    is_test_path "$path" && continue
    echo "$path"
    if [[ "$status" == "??" ]]; then
      rm -rf -- "${WT:?}/$path"
    else
      git -C "$WT" checkout -q HEAD -- "$path" 2>/dev/null || rm -rf -- "${WT:?}/$path"
    fi
  done < <(git -C "$WT" status --porcelain -z --untracked-files=all)
}

# --- Prompts -----------------------------------------------------------------------------------
write_impl_prompt() { # iter iter_dir feedback_file
  local iter="$1" dir="$2" feedback="$3"
  {
    cat <<EOF
You are the IMPLEMENTER agent in a fully automated design → implement → test loop for the ERMS
repository (your current directory is a dedicated git worktree). No human is available: never ask
questions, make reasonable decisions and record them. Follow .github/copilot-instructions.md,
CONTRIBUTING.md and the contract docs (docs/ARCHITECTURE.md, docs/API.md, docs/DATA_MODEL.md).

TASK:
$TASK

This is iteration $iter of $MAX_ITER.
EOF
    if [[ "$iter" -eq 1 ]]; then
      cat <<EOF

PHASE 1, DESIGN. Write the design to $RUN_DIR/design.md: scope and assumptions, numbered testable
acceptance criteria, API / data-model / UI changes, files to change, and the test plan (unit and
functional tests per acceptance criterion). Update the contract docs in the repository the way the
repo conventions require (API.md, DATA_MODEL.md, an ADR when a decision changes, CHANGELOG.md).
EOF
    else
      cat <<EOF

The previous round did not pass. Re-read the design at $RUN_DIR/design.md (update it if the
feedback shows it is wrong or incomplete) and fix EVERY issue in the feedback below. Tests added
by the tester are the specification of the defects: make them pass by fixing production code. Do
not delete or weaken them unless one is demonstrably wrong, and then justify it in your summary.
If an issue is out of scope for the task, contradicts the contract docs, or is a contrived input
no real client sends, you may dispute it instead of fixing it: list it under "## Disputed" in your
summary with the reasoning. Never dispute a failing verification (the test suites must pass).

FEEDBACK FROM THE PREVIOUS ROUND:
$(cat "$feedback")
EOF
    fi
    cat <<EOF

PHASE 2, IMPLEMENT the production code plus tests:
- unit tests (backend *Test with Mockito / @ErmsWebMvcTest slices; frontend Vitest for pure logic)
- functional tests (backend *IT extending AbstractIntegrationTest; frontend page tests through the
  real route table with MSW handlers)

PHASE 3, SELF-CHECK: run make be-format and/or make fe-format, then the targeted tests and the
relevant suites (commands are in .github/copilot-instructions.md) until they pass.

RULES: do not run git commit/push/checkout/reset or switch branches (the orchestrator commits);
do not deploy or touch Kubernetes; no secrets; keep changes focused on the task.

FINALLY write $dir/impl-summary.md: what changed (files), decisions and assumptions, how each
acceptance criterion is tested, and known limitations.
EOF
  } >"$dir/impl-prompt.md"
}

write_test_prompt() { # iter iter_dir verify_status verify_log
  local iter="$1" dir="$2" vstatus="$3" vlog="$4" prior="" k
  if [[ "$iter" -gt 1 ]]; then
    prior=" Your earlier reports:"
    for ((k = 1; k < iter; k++)); do prior+=" $RUN_DIR/iter-$k/test-report.md"; done
    prior+="
  FIRST re-check every issue from your previous report: fixed, still open, or disputed in the
  implementer summary. Accept a dispute when its reasoning is sound. Do not reopen resolved
  points or escalate the severity of points you already raised."
  fi
  cat >"$dir/test-prompt.md" <<EOF
You are the TESTER agent: independent QA in a fully automated build loop for the ERMS repository
(your current directory is the git worktree with the change). You did not write this code; be
skeptical. No human is available: never ask questions.

ORIGINAL TASK:
$TASK

Inputs:
- Design and acceptance criteria: $RUN_DIR/design.md (it may itself be wrong or incomplete, so
  check it against the original task)
- Implementer summary: $dir/impl-summary.md
- The full change: git diff $BASE_COMMIT..HEAD  (this round only: git show HEAD)
- Orchestrator verification ("$VERIFY_CMD") result: $vstatus, log at $vlog
- This is round $iter of $MAX_ITER.${prior}

Do all of the following:
1. Check every acceptance criterion and the original task against the code and tests.
2. Run the tests yourself (targeted tests and the relevant suites; commands are in
   .github/copilot-instructions.md).
3. Probe edge cases: validation boundaries, error ProblemDetail status/code, authorization
   (invisible entities must be 404, not 403), omitted nulls, date handling via Clock, concurrency
   and state transitions, regressions in existing behaviour, frontend loading/empty/error states.
   Write NEW tests that expose each real defect you find. You may ONLY create or modify test
   files: backend/src/test/**, frontend/src/test/** and frontend/src/**/*.test.ts(x). Any other
   change is reverted automatically. Failing tests that demonstrate real defects are wanted.
4. Review conventions: .github/copilot-instructions.md, CONTRIBUTING.md, and that the contract
   docs (docs/API.md etc.) and CHANGELOG.md were updated.
Do not run git commit/push/checkout/reset.

Write your report to $dir/test-report.md. Its FIRST line must be exactly "VERDICT: PASS" or
"VERDICT: FAIL". PASS only when every acceptance criterion is met, all tests pass (including the
ones you added) and there is no BLOCKER or MAJOR issue. Then list each issue as
"[BLOCKER|MAJOR|MINOR] <file:line> <what is wrong> <expected>", followed by the tests you added and
what each proves. Be specific enough that another engineer can fix each issue without asking you.

Severity (be calibrated; the loop only stops on PASS):
- BLOCKER: build or tests fail, data loss/corruption, security hole (auth bypass, IDOR, injection).
- MAJOR: an acceptance criterion or the original task is not met, the documented contract
  (docs/API.md, DATA_MODEL.md) is violated for inputs a real client would send, a regression in
  existing behaviour, or required tests/docs are missing.
- MINOR: everything else, including contrived inputs no real client sends, style, wording and
  nice-to-have hardening. MINOR issues never cause FAIL.
EOF
}

# --- Loop --------------------------------------------------------------------------------------
FEEDBACK=""
CONVERGED=false
for ((i = 1; i <= MAX_ITER; i++)); do
  IT_DIR="$RUN_DIR/iter-$i"
  mkdir -p "$IT_DIR"
  log "===== Iteration $i/$MAX_ITER ====="

  write_impl_prompt "$i" "$IT_DIR" "$FEEDBACK"
  if ! run_agent implementer "$IMPL_MODEL" "$IT_DIR/impl-prompt.md" "$IT_DIR"; then
    echo "Implementer failed twice; stopping."; break
  fi
  commit_all "feat(agent-loop): implementation, iteration $i" || echo "Implementer made no changes"

  VSTATUS=PASSED
  verify "$IT_DIR/verify.log" || VSTATUS=FAILED
  log "Verification $VSTATUS"

  write_test_prompt "$i" "$IT_DIR" "$VSTATUS" "$IT_DIR/verify.log"
  if ! run_agent tester "$TEST_MODEL" "$IT_DIR/test-prompt.md" "$IT_DIR"; then
    echo "Tester failed twice; stopping."; break
  fi

  REVERTED="$(enforce_tester_scope)"
  TESTER_CHANGED=false
  commit_all "test(agent-loop): tester tests, iteration $i" && TESTER_CHANGED=true

  VERDICT="$(head -n1 "$IT_DIR/test-report.md" 2>/dev/null | tr -d '\r' || true)"
  [[ "$VERDICT" == "VERDICT: PASS" ]] || VERDICT="VERDICT: FAIL"

  VSTATUS2="$VSTATUS"
  if [[ "$VSTATUS" == PASSED && "$VERDICT" == "VERDICT: PASS" ]] && $TESTER_CHANGED; then
    verify "$IT_DIR/verify-after-tester.log" || VSTATUS2=FAILED
    log "Verification after tester tests: $VSTATUS2"
  fi
  log "Tester $VERDICT, verification $VSTATUS2"

  if [[ "$VSTATUS2" == PASSED && "$VERDICT" == "VERDICT: PASS" ]]; then
    CONVERGED=true; break
  fi

  FEEDBACK="$IT_DIR/feedback.md"
  {
    echo "## Tester report (round $i)"
    cat "$IT_DIR/test-report.md" 2>/dev/null || echo "(the tester wrote no report; treat as FAIL)"
    if [[ "$VSTATUS" == FAILED ]]; then
      echo; echo "## Verification failed after your changes: last 150 lines of $IT_DIR/verify.log"
      tail -n 150 "$IT_DIR/verify.log"
    fi
    if [[ -f "$IT_DIR/verify-after-tester.log" && "$VSTATUS2" == FAILED ]]; then
      echo; echo "## Verification failed with the tester's new tests: last 150 lines"
      tail -n 150 "$IT_DIR/verify-after-tester.log"
    fi
    if [[ -n "$REVERTED" ]]; then
      echo; echo "## Note: non-test changes by the tester were reverted:"; echo "$REVERTED"
    fi
  } >"$FEEDBACK"
done

# --- Wrap-up -----------------------------------------------------------------------------------
COMMITS="$(git -C "$WT" log --oneline "$BASE_COMMIT..HEAD" | cat)"
{
  echo "# agent-loop run $RUN_ID"
  echo "- Result: $($CONVERGED && echo "CONVERGED after $i iteration(s)" || echo "NOT CONVERGED")"
  echo "- Branch: $BRANCH (base: $BASE_LABEL, ${BASE_COMMIT:0:12})"
  echo "- Worktree: $WT"
  echo "- Models: implementer $IMPL_MODEL, tester $TEST_MODEL"
  echo; echo '## Commits'; echo "$COMMITS"
} >"$RUN_DIR/summary.md"
cat "$RUN_DIR/summary.md"

if $CONVERGED && $OPEN_PR; then
  log "Opening a draft pull request"
  PR_BODY="$RUN_DIR/pr-body.md"
  cat >"$RUN_DIR/pr-prompt.md" <<EOF
Write a pull request description to $PR_BODY for the change on this branch (git diff
$BASE_COMMIT..HEAD). Use .github/pull_request_template.md exactly: keep its headings, comments,
checklist items and order, and fill them in accurately. Validation evidence comes from
$RUN_DIR/iter-$i/verify.log ("$VERIFY_CMD", PASSED) and $RUN_DIR/iter-$i/test-report.md. Write only that file.
EOF
  run_agent pr-writer "$IMPL_MODEL" "$RUN_DIR/pr-prompt.md" "$RUN_DIR" || true
  [[ -s "$PR_BODY" ]] || cp "$RUN_DIR/summary.md" "$PR_BODY"
  git -C "$WT" push -u origin "$BRANCH"
  (cd "$WT" && gh pr create --draft --base "${BASE#origin/}" --head "$BRANCH" \
      --title "$(printf '%s' "$TASK" | head -n1 | cut -c1-72)" --body-file "$PR_BODY")
fi

echo
echo "Inspect: cd $WT && git log --stat $BASE_COMMIT..HEAD"
echo "Clean up: git worktree remove --force $WT && git branch -D $BRANCH"
$CONVERGED
