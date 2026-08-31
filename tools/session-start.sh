#!/bin/bash
# Runs at session start. Puts the workspace back where the last session left it.
#
# Why this exists. The session runs in a container that is reclaimed after a while and then
# restored from an earlier snapshot, which puts the clone back to whatever it held then. On
# 2026-08-31 that cost three commits that had not been pushed. The remote is the only durable
# copy of anything, so the first thing a session should do is agree with it.
#
# Two jobs, both conservative - this never discards work:
#   1. Fast-forward the checked-out branch to the remote if it is behind and the tree is clean.
#      A dirty tree or a diverged branch is left alone and reported instead.
#   2. Drop a repo-local git identity that disagrees with the global one. The snapshot carries
#      the clone's original .git/config, whose user.email is the account holder's; commits made
#      under it show as Unverified on GitHub.
set -u
cd "$(dirname "$0")/.." || exit 0

global=$(git config --global --get user.email 2>/dev/null)
local=$(git config --local --get user.email 2>/dev/null)
if [[ -n "$global" && -n "$local" && "$global" != "$local" ]]; then
  git config --local --unset user.email 2>/dev/null
  git config --local --unset user.name 2>/dev/null
  echo "session-start: dropped the repo-local git identity ($local); using $global"
fi

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
[[ -z "$branch" || "$branch" == "HEAD" ]] && exit 0

for wait in 2 4 8 0; do
  git fetch --quiet origin "$branch" 2>/dev/null && break
  [[ "$wait" == 0 ]] && { echo "session-start: could not reach origin"; exit 0; }
  sleep "$wait"
done

here=$(git rev-parse HEAD)
there=$(git rev-parse FETCH_HEAD 2>/dev/null) || exit 0
[[ "$here" == "$there" ]] && exit 0

if [[ -n "$(git status --porcelain)" ]]; then
  echo "session-start: $branch differs from origin but the tree is dirty - left alone."
  echo "  local $here"
  echo "  origin $there"
  exit 0
fi

if git merge-base --is-ancestor "$here" "$there"; then
  git merge --ff-only "$there" >/dev/null 2>&1 \
    && echo "session-start: fast-forwarded $branch to $there (the snapshot was behind)."
else
  echo "session-start: $branch has diverged from origin - resolve by hand."
  echo "  local $here"
  echo "  origin $there"
fi
exit 0
