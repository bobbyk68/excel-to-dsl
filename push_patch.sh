#!/usr/bin/env bash
set -euo pipefail

# Usage: ./push_patch.sh <label> [baseRef] [patchBranch]
# Example: ./push_patch.sh dslgen-update origin/main patches
PATCH_LABEL="${1:-dslgen-update}"
BASE_REF="${2:-origin/main}"
PATCH_BRANCH="${3:-patches}"
DATE="$(date +%Y-%m-%d)"
PATCH_NAME="${DATE}-${PATCH_LABEL}.patch"

git rev-parse --is-inside-work-tree >/dev/null || { echo "Not in a git repo"; exit 1; }
CURRENT_BRANCH="$(git branch --show-current || true)"
[[ -n "$CURRENT_BRANCH" ]] || { echo "Detached HEAD; checkout a branch (e.g., main) first"; exit 1; }

mkdir -p patches
git format-patch "$BASE_REF" --stdout > "patches/${PATCH_NAME}"
echo "Created patches/${PATCH_NAME}"

git fetch --all -q

WT_DIR=".git/_wt_${PATCH_BRANCH}"
if git rev-parse --verify "$PATCH_BRANCH" >/dev/null 2>&1; then
  [[ -d "$WT_DIR" ]] || git worktree add "$WT_DIR" "$PATCH_BRANCH"
else
  git worktree add -b "$PATCH_BRANCH" "$WT_DIR" --no-checkout
  git -C "$WT_DIR" read-tree --empty
  git -C "$WT_DIR" checkout --orphan "$PATCH_BRANCH"
fi

mkdir -p "$WT_DIR/patches"
cp "patches/${PATCH_NAME}" "$WT_DIR/patches/"
git -C "$WT_DIR" add patches
git -C "$WT_DIR" commit -m "patch: ${PATCH_LABEL} (${DATE})" || echo "No changes to commit"

git -C "$WT_DIR" push -u origin "$PATCH_BRANCH"

echo
echo "✅ Pushed patch to branch: $PATCH_BRANCH"
echo "Apply at work with:"
echo "  git fetch origin $PATCH_BRANCH"
echo "  git show origin/${PATCH_BRANCH}:patches/${PATCH_NAME} | git am --3way"
