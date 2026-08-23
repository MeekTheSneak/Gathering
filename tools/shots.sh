#!/usr/bin/env bash
#
# Drives the client through a scripted session and leaves pictures of it.
#
# The gate proves the code is right and the preview proves the layout is right. Neither can
# say whether the thing a player actually sees looks like anything, because that involves
# textures, fonts, the real camera and the real card art - and until now the only way to find
# out was to ask somebody to open the game and describe it.
#
#   tools/shots.sh              # boot, set a table up, photograph it, quit
#
# Pictures land in neoforge/run/screenshots. Needs xvfb-run and software GL, same as smoke.sh.
set -uo pipefail
cd "$(dirname "$0")/.."

OUT=neoforge/run/screenshots
rm -rf "$OUT" neoforge/run/saves/GatheringDevScene

if ! command -v xvfb-run >/dev/null 2>&1; then
    echo "needs xvfb-run"; exit 1
fi

timeout "${SHOT_SECONDS:-420}" xvfb-run -a -s "-screen 0 1280x800x24" \
    env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 \
    ./gradlew :neoforge:runClient -Pdevscene > /tmp/gathering-shots.log 2>&1

grep -E '^\[devscene\]' /tmp/gathering-shots.log

if [ -d "$OUT" ] && [ -n "$(ls -A "$OUT" 2>/dev/null)" ]; then
    echo
    ls -1 "$OUT"
else
    echo "no pictures taken; see /tmp/gathering-shots.log"
    exit 1
fi
