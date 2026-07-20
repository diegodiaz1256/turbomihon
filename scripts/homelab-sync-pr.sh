#!/usr/bin/env bash
# Upstream-sync script for a personal Mihon fork. Run on a schedule (cron/systemd timer).
#
# Does NOT build or release anything. Each run:
#   1. Fetches upstream mihonapp/mihon.
#   2. If there's anything new, creates/updates a branch merging upstream into main
#      and opens (or updates) a PR against the fork's own main branch.
#   3. You review + resolve conflicts (if any) in the GitHub UI, then merge yourself.
#
# Merging the PR is what triggers the actual build+release - see
# .github/workflows/fork-release.yml, which runs on push to main.
#
# Requirements: git, gh (authenticated, repo scope).

set -euo pipefail

REPO_DIR="${REPO_DIR:-$HOME/mihon}"
UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/mihonapp/mihon.git}"
BASE_BRANCH="${BASE_BRANCH:-main}"
SYNC_BRANCH="upstream-sync"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

cd "$REPO_DIR"

git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "$UPSTREAM_URL"
git fetch origin "$BASE_BRANCH"
git fetch upstream

if git rev-parse --verify "upstream/main" >/dev/null 2>&1; then
    UPSTREAM_REF="upstream/main"
else
    UPSTREAM_REF="upstream/master"
fi

# Nothing new upstream compared to what's already on origin/main - skip.
if git merge-base --is-ancestor "$UPSTREAM_REF" "origin/$BASE_BRANCH"; then
    log "No new upstream commits. Nothing to do."
    exit 0
fi

# If a sync PR is already open and still up to date with the latest upstream, don't spam a new one.
if git rev-parse --verify "origin/$SYNC_BRANCH" >/dev/null 2>&1; then
    git fetch origin "$SYNC_BRANCH"
    if git merge-base --is-ancestor "$UPSTREAM_REF" "origin/$SYNC_BRANCH"; then
        log "Sync branch already up to date with upstream, existing PR (if any) still valid."
        exit 0
    fi
fi

git checkout -B "$SYNC_BRANCH" "origin/$BASE_BRANCH"

MERGE_OK=true
git merge --no-edit "$UPSTREAM_REF" || MERGE_OK=false

git push --force-with-lease origin "$SYNC_BRANCH"

EXISTING_PR="$(gh pr list --head "$SYNC_BRANCH" --state open --json number -q '.[0].number' || true)"

if [ "$MERGE_OK" = true ]; then
    BODY="Automated sync from upstream mihonapp/mihon @ \$(git rev-parse --short $UPSTREAM_REF). Merged cleanly, no conflicts."
else
    BODY="Automated sync from upstream mihonapp/mihon @ \$(git rev-parse --short $UPSTREAM_REF). **Merge conflicts present** - resolve them in this PR (edit files here or pull the branch locally) before merging."
fi

if [ -n "$EXISTING_PR" ]; then
    log "Updated existing sync PR #$EXISTING_PR"
    gh pr comment "$EXISTING_PR" --body "$BODY"
else
    log "Opening new sync PR"
    gh pr create \
        --base "$BASE_BRANCH" \
        --head "$SYNC_BRANCH" \
        --title "Sync with upstream mihonapp/mihon" \
        --body "$BODY"
fi

log "Done."
