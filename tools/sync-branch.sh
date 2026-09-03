#!/bin/bash
# Agrees with the remote before anything is written. Runs as a PreToolUse hook on Edit|Write.
#
# Why this exists, and why session-start.sh is not enough. The session runs in a container that
# is reclaimed and restored from an earlier snapshot, which puts the clone back to whatever it
# held then - on 2026-09-03 that was 142 commits behind, mid-turn. session-start.sh catches it
# at the start of a session; nothing caught it after. So the work carried on against a tree
# that had silently gone backwards: edits landing on old code, and a report of what the code
# says that was true of a version no longer on disk.
#
# The check has to reach the remote, because the remote is the only thing a snapshot cannot
# roll back. A fetch is about four hundred milliseconds, which is too much to spend before
# every edit, so it is spent at most once every WINDOW seconds. The marker recording when it
# was last spent lives in .git, so a restored snapshot brings back an old one and the very next
# edit checks - which is the case this exists for.
#
# Never blocks. A tool call held up by a network problem would be worse than the staleness this
# is looking for, so every path here exits 0.
set -u
cd "$(dirname "$0")/.." 2>/dev/null || exit 0

# How long an agreement with the remote is trusted for, in seconds.
WINDOW=90

MARKER=".git/gathering-synced"

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null) || exit 0
[[ -z "$branch" || "$branch" == "HEAD" ]] && exit 0
here=$(git rev-parse HEAD 2>/dev/null) || exit 0
now=$(date +%s)

# The cheap path, which is almost every call: checked recently, and nothing has moved since.
if [[ -r "$MARKER" ]]; then
  read -r when sha _ < "$MARKER" 2>/dev/null || true
  if [[ "${when:-0}" =~ ^[0-9]+$ && "${sha:-}" == "$here" ]] \
      && (( now - when >= 0 && now - when < WINDOW )); then
    exit 0
  fi
fi

timeout 20 git fetch --quiet origin "$branch" 2>/dev/null || exit 0
there=$(git rev-parse FETCH_HEAD 2>/dev/null) || exit 0

remember() {
  printf '%s %s\n' "$(date +%s)" "$(git rev-parse HEAD 2>/dev/null)" > "$MARKER" 2>/dev/null
}

if [[ "$here" == "$there" ]]; then
  remember
  exit 0
fi

say() {
  # Both at once: the message goes to the person watching, the context goes to the model that
  # is about to edit a file it may have the wrong version of in mind.
  python3 - "$1" <<'PY' 2>/dev/null || echo "$1"
import json, sys
note = sys.argv[1]
print(json.dumps({
    "systemMessage": note,
    "hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": note},
}))
PY
}

# Tracked changes only. An untracked file cannot be run over by a fast-forward - git refuses
# the merge itself if one is in the way - and a build directory or a run directory full of
# them is the ordinary state of this repo, so counting those would refuse every time.
if [[ -n "$(git status --porcelain --untracked-files=no 2>/dev/null)" ]]; then
  remember
  say "This checkout is not the branch on origin and has uncommitted changes, so it was left
alone. Local $here, origin $there. Work out which is right before writing anything else."
  exit 0
fi

if ! git merge-base --is-ancestor "$here" "$there" 2>/dev/null; then
  remember
  say "This checkout has diverged from origin and cannot be fast-forwarded.
Local $here, origin $there. Resolve it before writing anything else."
  exit 0
fi

behind=$(git rev-list --count "$here..$there" 2>/dev/null)
if ! git merge --ff-only "$there" >/dev/null 2>&1; then
  remember
  say "This clone is $behind commit(s) behind origin and would not fast-forward - usually an
untracked file sitting where an incoming commit adds one. Local $here, origin $there."
  exit 0
fi

remember
say "The container was restored from a snapshot: this clone was $behind commit(s) behind
origin and has been fast-forwarded to $there. Anything you remember about the files here was
read from the older tree - re-read before editing, and do not trust a summary of the code
written before this point."
exit 0
