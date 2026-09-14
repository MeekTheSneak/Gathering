#!/usr/bin/env bash
# Everything that has to pass before a change is reported as working, in one command.
#
# It exists because reading a build's output for the wrong word is a real way to report a
# green gate that is not one: `./gradlew build -q | grep error:` says nothing at all when two
# tests fail, because a failing test prints "FAILED". This looks at exit codes, which cannot
# be misread, and says plainly which stage went wrong.
#
#   tools/gate.sh            the gate: everything below
#   tools/gate.sh --quick    compile, unit tests and the static checks only, for iterating
#
# The gate runs `./gradlew verify`, not `./gradlew build`. That difference mattered. For a long
# time this script ran `build`, which does not include data generation, does not include
# Fabric's in-world tests, and does not include verify's own check that it still covers what it
# claims - so "gate green" meant one loader's game tests and no datagen, while a second gate,
# `verify`, existed beside it covering different ground. An audit found the two disagreeing.
# There is one gate now, and --quick is explicitly not it.
#
# Never runs the scripted client: that holds neoforge/run for a quarter of an hour and the
# game tests fight it for the same directory. Run tools/shots.sh separately.
set -uo pipefail
cd "$(dirname "$0")/.."

QUICK=0
if [ "${1:-}" = "--quick" ]; then
    QUICK=1
fi

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

#: Where the build's own output is kept, so the in-world results can be read back out of it.
GRADLE_LOG=$(mktemp -t gathering-gate)
trap 'rm -f "$GRADLE_LOG"' EXIT

# The Gradle half, as one task: verify is the one that knows what it has to cover and fails
# when a dependency is dropped, renamed or wired to the wrong project.
if [ "$QUICK" = 1 ]; then
    GRADLE_STAGE="gradle build (unit tests only)"
    GRADLE_TASK="build"
else
    GRADLE_STAGE="gradle verify (both loaders)"
    GRADLE_TASK="verify"
fi
printf '%-34s' "$GRADLE_STAGE"
if ./gradlew "$GRADLE_TASK" > "$GRADLE_LOG" 2>&1; then
    printf 'ok\n'
else
    printf 'FAILED\n'
    failed+=("$GRADLE_STAGE")
    grep -iE 'FAILED|error:|expected|but was' "$GRADLE_LOG" | head -12
fi

for check in langcheck doccheck scenecheck plotcheck gesturecheck spritecheck statecheck \
             savecheck runcheck texturecheck artcheck tablecheck keycheck prefcheck; do
    stage "$check" python3 "tools/$check.py"
done

# A suite that discovered nothing passes. Both loaders print how many required tests ran, and a
# run reporting none - a renamed annotation, a source set that stopped being scanned, a
# registration quietly dropped - would otherwise read as a clean gate. Two loaders, both
# nonzero, or this is not a pass.
if [ "$QUICK" = 0 ]; then
    printf '%-34s' "in-world tests actually ran"
    counts=$(grep -oE 'All [0-9]+ required tests passed' "$GRADLE_LOG" | grep -oE '[0-9]+' || true)
    howMany=$(printf '%s' "$counts" | grep -c '[0-9]' || true)
    zeroes=$(printf '%s' "$counts" | grep -cx '0' || true)
    if [ "$howMany" -lt 2 ] || [ "$zeroes" -gt 0 ]; then
        printf 'FAILED\n'
        failed+=("in-world tests actually ran")
        echo "  expected both loaders to report a nonzero count; saw: ${counts:-none}"
    else
        printf 'ok (%s)\n' "$(printf '%s' "$counts" | tr '\n' '/')"
    fi

    # A table's tick and a tournament's clock keep their failures to themselves rather than
    # crashing the server, which also means a failure in them no longer stops an in-world test
    # run. So the gate reads the run for them instead. The one exception is the test that
    # breaks a clock on purpose, and it names its tournament so.
    printf '%-34s' "no failures kept out of a tick"
    quiet=$(grep -E 'went wrong in its tick|could not run its clock this tick' "$GRADLE_LOG" \
        | grep -v 'DELIBERATELY BROKEN' || true)
    if [ -n "$quiet" ]; then
        printf 'FAILED\n'
        failed+=("no failures kept out of a tick")
        printf '%s\n' "$quiet" | head -6
    else
        printf 'ok\n'
    fi
fi

echo
if [ ${#failed[@]} -eq 0 ]; then
    if [ "$QUICK" = 1 ]; then
        echo "quick checks green - this is NOT the gate; run tools/gate.sh"
    else
        echo "gate green"
    fi
    exit 0
fi
echo "gate RED: ${failed[*]}"
exit 1
