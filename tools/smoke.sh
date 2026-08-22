#!/usr/bin/env bash
#
# Boots everything the mod claims to run on, and says whether it did.
#
# The gate (./gradlew verify) proves the code is right. It cannot prove a loader was wired
# up right, because a loader that serves the mod's classes without its assets compiles,
# builds, passes every test, boots, registers everything and logs happily - and then draws
# missing textures and raw translation keys. Fabric shipped exactly that until somebody read
# the resource pack list on startup.
#
# So: run this before claiming a loader works.
#
#   tools/smoke.sh            # every target
#   tools/smoke.sh fabric     # just one
#
# Needs a virtual display for the client boots (xvfb-run) and software GL.

set -uo pipefail
cd "$(dirname "$0")/.."

LOGS="$(mktemp -d)"
BOOT_SECONDS="${BOOT_SECONDS:-180}"
FAILED=0

have_display() { command -v xvfb-run >/dev/null 2>&1; }

# A boot that is still running when the timer runs out is a boot that worked: these targets
# never exit on their own. An early exit means it died.
boot() {
    local name="$1" task="$2" needs_display="$3"
    shift 3
    local log="$LOGS/$name.log"

    printf '%-24s ' "$name"
    if [ "$needs_display" = yes ] && ! have_display; then
        echo "SKIPPED (no xvfb-run)"
        return
    fi

    if [ "$needs_display" = yes ]; then
        timeout "$BOOT_SECONDS" xvfb-run -a -s "-screen 0 1280x800x24" \
            env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 \
            ./gradlew "$task" >"$log" 2>&1
    else
        timeout "$BOOT_SECONDS" ./gradlew "$task" >"$log" 2>&1
    fi
    local code=$?

    if [ "$code" != 124 ]; then
        echo "FAILED (exited $code before the timer; see $log)"
        FAILED=1
        return
    fi

    # Patterns, not fixed strings. The check that matters here is that the mod's resource
    # pack is in the list the game loaded, and the mod id on its own appears all over a log
    # for reasons that have nothing to do with whether its assets were found.
    local missing=()
    for expected in "$@"; do
        grep -qiE -- "$expected" "$log" || missing+=("$expected")
    done

    # Errors that are the environment rather than the mod, matched on the error line itself
    # rather than counted across the whole log - a stack trace under one ignorable error
    # would otherwise buy silence for a real one somewhere else.
    #
    #   narrator / flite / SoundSystem - no speech or audio hardware in a container
    #   server.properties              - vanilla logs its own first run as an error, then
    #                                    writes the file and carries on
    local errors
    errors=$(grep -E '^\[.*(ERROR|FATAL)' "$log" 2>/dev/null \
        | grep -vEi 'narrator|soundsystem|soundengine|flite|server\.properties' \
        | wc -l)

    if [ "${#missing[@]}" -ne 0 ]; then
        echo "FAILED (never said: ${missing[*]}; see $log)"
        FAILED=1
    elif [ "$errors" -gt 0 ]; then
        echo "FAILED ($errors unexpected error line(s); see $log)"
        FAILED=1
    else
        echo "ok"
    fi
}

TARGET="${1:-all}"

# Before booting anything: every translation key the source asks for has an entry. A missing
# one is the other half of the failure this script exists for - the assets load, the game runs,
# and a screen draws "screen.gathering.table.key_flip" where a sentence should be. Nothing else
# in the build catches it, and a boot only catches it if somebody happens to open that screen.
printf '%-24s ' "translation keys"
if LANG_OUT=$(python3 tools/langcheck.py 2>&1); then
    echo "ok"
else
    echo "FAILED"
    echo "$LANG_OUT" | sed 's/^/    /'
    FAILED=1
fi

if [ "$TARGET" = all ] || [ "$TARGET" = neoforge ]; then
    # "mod/gathering" is the mod's own resource pack. Without it the assets are not loaded.
    # The camera hook is a mixin, and a mixin that is never listed fails silently: the mod
    # boots, everything registers, and the camera just never moves. So the client boot checks
    # it went in.
    boot "neoforge client" ":neoforge:runClient" yes \
        "Reloading ResourceManager:.*mod/gathering" "Gathering loaded" \
        "Gathering camera hook installed"
    boot "neoforge server" ":neoforge:runServer" no "Done \\(" "Gathering loaded"
fi

if [ "$TARGET" = all ] || [ "$TARGET" = fabric ]; then
    boot "fabric client" ":fabric:runClient" yes \
        "Reloading ResourceManager:.*[ ,]gathering" "Gathering loaded" \
        "Gathering camera hook installed"
    boot "fabric server" ":fabric:runServer" no "Done \\(" "Gathering loaded"
fi

echo
if [ "$FAILED" = 0 ]; then
    echo "All boots came up. Logs: $LOGS"
else
    echo "Something did not come up. Logs: $LOGS"
fi
exit "$FAILED"
