#!/usr/bin/env bash
# Everything that has to pass before a change is reported as working, in one command.
#
# It exists because reading a build's output for the wrong word is a real way to report a
# green gate that is not one: `./gradlew build -q | grep error:` says nothing at all when two
# tests fail, because a failing test prints "FAILED". This looks at exit codes, which cannot
# be misread, and says plainly which stage went wrong.
#
#   tools/gate.sh            build, the unit tests, and the eight checks
#   tools/gate.sh --game     and the in-world game tests, which want neoforge/run to itself
#
# Never runs the scripted client: that holds neoforge/run for a quarter of an hour and the
# game tests fight it for the same directory. Run tools/shots.sh separately.
set -u
cd "$(dirname "$0")/.."

failed=()
stage() {
    local name="$1"; shift
    printf '%-34s' "$name"
    if out=$("$@" 2>&1); then
        printf 'ok\n'
    else
        printf 'FAILED\n'
        failed+=("$name")
        printf '%s\n' "$out" | grep -iE 'FAILED|error:|^\s+[A-Za-z].*Error|expected|but was' | head -12
    fi
}

stage "gradle build (all unit tests)" ./gradlew build
for check in langcheck doccheck scenecheck plotcheck gesturecheck spritecheck statecheck texturecheck; do
    stage "$check" python3 "tools/$check.py"
done
if [ "${1:-}" = "--game" ]; then
    stage "in-world game tests" ./gradlew :neoforge:runGameTestServer
fi

echo
if [ ${#failed[@]} -eq 0 ]; then
    echo "gate green"
    exit 0
fi
echo "gate RED: ${failed[*]}"
exit 1
