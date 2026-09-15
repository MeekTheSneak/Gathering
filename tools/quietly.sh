#!/usr/bin/env bash
#
# Runs a scripted client without taking the screen or the speakers.
#
#   tools/quietly.sh neoforge/run ./gradlew :neoforge:runClient -Pdevscene
#   tools/quietly.sh neoforge/run ./gradlew :neoforge:runClient -Paccessibilityprobe
#   tools/quietly.sh neoforge/runs/pack ./gradlew :neoforge:runPackClient -Ppackscene
#
# The first argument is the run's game directory, the rest is the command. Asked for by the owner,
# who works on the same machine while these run: a client that starts in front of whatever they
# are doing, plays its music and takes their keystrokes is in the way.
#
# Sound: the game directory's master volume is set to nothing before the game reads its options,
# so not even the first note of the menu music plays, and put back as it was afterwards - also
# when the run is interrupted. The spoken welcome is turned off for good (see below).
#
# The screen: a window cannot be told from outside to open behind, so this watches instead. While
# the command runs, whenever the game has come to the front, the app that was in front before it
# is put back - through AppKit's own call for activating an app, which, unlike an Apple event, asks
# for no permission. The game keeps drawing behind; the scripted runs do not need focus, and they
# set pauseOnLostFocus off. On a machine without lsappinfo (not a Mac), only the sound is done.
#
# Screenshots are taken from the game's own framebuffer, so a window behind others photographs the
# same as one in front.
set -uo pipefail
cd "$(dirname "$0")/.."

if [ $# -lt 2 ]; then
    echo "usage: tools/quietly.sh <game directory> <command...>"; exit 2
fi
RUN_DIR="$1"; shift
OPTIONS="$RUN_DIR/options.txt"
mkdir -p "$RUN_DIR"

# What the volume and focus settings were, to put back.
WAS_VOLUME=""
WAS_PAUSE=""
if [ -f "$OPTIONS" ]; then
    WAS_VOLUME=$(grep '^soundCategory_master:' "$OPTIONS" || true)
    WAS_PAUSE=$(grep '^pauseOnLostFocus:' "$OPTIONS" || true)
fi

set_option() {
    local key="$1" value="$2"
    if [ -f "$OPTIONS" ] && grep -q "^$key:" "$OPTIONS"; then
        sed -i.quietly "s/^$key:.*/$key:$value/" "$OPTIONS" && rm -f "$OPTIONS.quietly"
    else
        echo "$key:$value" >> "$OPTIONS"
    fi
}

restore() {
    if [ -n "$WAS_VOLUME" ]; then
        set_option soundCategory_master "${WAS_VOLUME#soundCategory_master:}"
    fi
    if [ -n "$WAS_PAUSE" ]; then
        set_option pauseOnLostFocus "${WAS_PAUSE#pauseOnLostFocus:}"
    fi
    if [ -n "${WATCHER:-}" ]; then
        kill "$WATCHER" 2>/dev/null
    fi
}
trap restore EXIT
trap 'exit 130' INT TERM

set_option soundCategory_master 0.0
set_option pauseOnLostFocus false
# Minecraft's first-launch accessibility screen reads itself aloud - "press Enter to enable the
# narrator" - through the system's speech, which the game's volume does not touch. It comes back on
# every run because a scripted run replaces that screen rather than answering it, so the option that
# says it has been seen is never written. Answered here for good, not put back: a development game
# directory has no first launch to welcome anybody to. The narrator itself stays off.
set_option onboardAccessibility false
set_option narrator 0

front_pid() {
    lsappinfo info -only pid "$(lsappinfo front)" 2>/dev/null | sed -n 's/.*"pid"=\([0-9]*\).*/\1/p'
}

is_game() {
    # The client is a bare java process: no bundle, named java.
    lsappinfo info -only name "$(lsappinfo front)" 2>/dev/null | grep -q '"java"'
}

activate() {
    osascript -l JavaScript -e "ObjC.import('AppKit'); \$.NSRunningApplication.runningApplicationWithProcessIdentifier($1).activateWithOptions(\$.NSApplicationActivateIgnoringOtherApps)" >/dev/null 2>&1
}

if command -v lsappinfo >/dev/null 2>&1; then
    (
        last=$(front_pid)
        while true; do
            if is_game; then
                if [ -n "$last" ]; then
                    activate "$last"
                fi
            else
                now=$(front_pid)
                if [ -n "$now" ]; then
                    last=$now
                fi
            fi
            sleep 0.2
        done
    ) &
    WATCHER=$!
fi

"$@"
