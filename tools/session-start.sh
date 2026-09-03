#!/bin/bash
# Runs at session start. Puts the workspace back where the last session left it.
#
# Two jobs. Agreeing with the remote is sync-branch.sh's, which also runs before every edit -
# see the reasoning there. This adds the one thing that only matters once, at the start:
# dropping a repo-local git identity that disagrees with the global one. The snapshot carries
# the clone's original .git/config, whose user.email is the account holder's; commits made
# under it show as Unverified on GitHub.
set -u
cd "$(dirname "$0")/.." || exit 0

global=$(git config --global --get user.email 2>/dev/null)
local=$(git config --local --get user.email 2>/dev/null)
if [[ -n "$global" && -n "$local" && "$global" != "$local" ]]; then
  git config --local --unset user.email 2>/dev/null
  git config --local --unset user.name 2>/dev/null
  echo "session-start: dropped the repo-local git identity ($local); using $global"
fi

# Whatever the last session left is older than the window, so this always really checks.
rm -f .git/gathering-synced 2>/dev/null
bash "$(dirname "$0")/sync-branch.sh"
exit 0
