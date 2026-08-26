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

timeout "${SHOT_SECONDS:-720}" xvfb-run -a -s "-screen 0 1280x800x24" \
    env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 \
    ./gradlew :neoforge:runClient -Pdevscene > /tmp/gathering-shots.log 2>&1

# The world goes with it. It holds a table with a live game in it, and the game test server
# runs in this same directory: left behind, that table ticks on a server with no client to
# broadcast to, and the whole gate crashes on a leftover from a picture.
rm -rf neoforge/run/saves/GatheringDevScene

grep -E '^\[devscene\]' /tmp/gathering-shots.log

if [ ! -d "$OUT" ] || [ -z "$(ls -A "$OUT" 2>/dev/null)" ]; then
    echo "no pictures taken; see /tmp/gathering-shots.log"
    exit 1
fi

echo
ls -1 "$OUT"

# The run says what it expected at each step. Anything it did not get is a flow that has
# stopped working, and a script that only leaves a duller picture behind is one nobody reads.
if grep -q '^\[devscene\] FAIL' /tmp/gathering-shots.log; then
    echo
    echo "the scripted run did not get what it expected:"
    grep '^\[devscene\] FAIL' /tmp/gathering-shots.log | sort -u
    exit 1
fi

# And it got all the way to the end. The scene's dispatcher reads a step it has no case for
# as "finished", so a run that stops in the middle still prints "failures: 0" - which is true
# and worthless. tools/scenecheck.py stops the usual cause; this catches every other one,
# including the run simply timing out with the client still up.
REACHED=$(grep -o '^\[devscene\] reached step [0-9]* of [0-9]*' /tmp/gathering-shots.log | tail -1)
if [ -z "$REACHED" ]; then
    echo
    echo "the scripted run never finished; see /tmp/gathering-shots.log"
    exit 1
fi
if [ "$(echo "$REACHED" | awk '{print $4}')" != "$(echo "$REACHED" | awk '{print $6}')" ]; then
    echo
    echo "the scripted run stopped early: $REACHED"
    exit 1
fi
