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
#   LOADER=fabric tools/shots.sh   # the same run, on the other loader
#
# Pictures land in <loader>/run/screenshots. Needs xvfb-run and software GL, same as smoke.sh.
#
# Both loaders can drive it because the scene lives in :common. That is the point of running
# it on Fabric: smoke.sh proves that loader boots and registers, which says nothing about
# whether it plays - and a port that boots is the easiest kind to be wrong about.
set -uo pipefail
cd "$(dirname "$0")/.."

LOADER="${LOADER:-neoforge}"
if [ "$LOADER" != neoforge ] && [ "$LOADER" != fabric ]; then
    echo "LOADER must be neoforge or fabric"; exit 1
fi

OUT=$LOADER/run/screenshots
rm -rf "$OUT" "$LOADER/run/saves/GatheringDevScene"

if ! command -v xvfb-run >/dev/null 2>&1; then
    echo "needs xvfb-run"; exit 1
fi

timeout "${SHOT_SECONDS:-720}" xvfb-run -a -s "-screen 0 1280x800x24" \
    env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 \
    ./gradlew ":$LOADER:runClient" -Pdevscene > /tmp/gathering-shots.log 2>&1

# The world goes with it. It holds a table with a live game in it, and the game test server
# runs in this same directory: left behind, that table ticks on a server with no client to
# broadcast to, and the whole gate crashes on a leftover from a picture.
rm -rf "$LOADER/run/saves/GatheringDevScene"

# Fabric routes System.out through log4j, so its lines arrive with a timestamp and a
# [STDOUT] tag in front; NeoForge's arrive bare. Anchoring on the start of the line found
# NeoForge's and silently found none of Fabric's - a run that had gone perfectly reported as
# a run that never finished. So the marker is looked for wherever on the line it is.
scene() {
    grep -E '\[devscene\]' /tmp/gathering-shots.log
}

scene

if [ ! -d "$OUT" ] || [ -z "$(ls -A "$OUT" 2>/dev/null)" ]; then
    echo "no pictures taken; see /tmp/gathering-shots.log"
    exit 1
fi

echo
ls -1 "$OUT"

# The run says what it expected at each step. Anything it did not get is a flow that has
# stopped working, and a script that only leaves a duller picture behind is one nobody reads.
if scene | grep -q '\[devscene\] FAIL'; then
    echo
    echo "the scripted run did not get what it expected:"
    scene | grep '\[devscene\] FAIL' | sed 's/.*\[devscene\]/[devscene]/' | sort -u
    exit 1
fi

# And it got all the way to the end. The scene's dispatcher reads a step it has no case for
# as "finished", so a run that stops in the middle still prints "failures: 0" - which is true
# and worthless. tools/scenecheck.py stops the usual cause; this catches every other one,
# including the run simply timing out with the client still up.
REACHED=$(scene | grep -o '\[devscene\] reached step [0-9]* of [0-9]*' | tail -1)
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
