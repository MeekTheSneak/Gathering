#!/usr/bin/env python3
"""The dev scene's step numbers must run 0, 1, 2, ... with no holes.

DevScene is a switch on a step counter, and its default branch means "the scene is over".
Java cannot tell a step nobody wrote from a step past the end, so a hole in the middle reads
as the end: the run stops there, having photographed a third of the mod, and reports zero
failures. That happened. Two rounds of inserting steps renumbered the cases around them and
lost 31, and several commits afterwards claimed a clean shots run that had never reached step
32.

There is no way to catch this from inside the scene at runtime - by the time the hole is
reached the scene has already decided it finished - so it is caught here, before the build.

Also checks LAST_STEP, which the dispatcher's guard compares against. A constant that has to
be raised by hand is a constant that drifts; this is what stops it.

And that the scene's own scripted waiting still fits inside the timeout tools/shots.sh gives
it. Every advance() and every waited= is a deliberate pause, and they came to 12.7 minutes
against a 12-minute default budget: the command both CLAUDE.md and TESTING.md document could
not finish at its own defaults, and every run was killed by the timer and reported as "the
scripted run never finished". Steps are added to this scene constantly, so the sum grows
without anybody deciding to grow it.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCENE = ROOT / "common/src/devscene/java/dev/gathering/client/DevScene.java"

CASE = re.compile(r"^\s*case (\d+) ->", re.MULTILINE)
LAST = re.compile(r"private static final int LAST_STEP = (\d+);")

SHOTS = ROOT / "tools/shots.sh"

CONSTANT = re.compile(r"private static final int ([A-Z_]+) = (\d+);")
ADVANCE = re.compile(r"advance\(([^()]*)\)")
WAITED = re.compile(r"waited = ([^;]+);")
BUDGET = re.compile(r'BUDGET="\$\{SHOT_SECONDS:-(\d+)\}"')

#: Ticks a second. The scene's pauses are counted in client ticks.
A_SECOND = 20

#: How much of the budget the deliberate waiting may take. The rest is world generation, the
#: deck import, the GUI-scale sweeps and every frame dropped under software GL, none of which
#: this can measure - so the waiting alone filling three quarters of the timer is the point
#: at which the budget needs raising rather than the run needing shortening.
MOST_OF_IT = 0.75

#: The dispatcher, and only the dispatcher. Other switches in this file are numbered too -
#: the gallery walks a look's three screens by phase - and counting those as scene steps
#: reports every one of them as a step written twice.
DISPATCH = "switch (step) {"


def dispatcher(source):
    """Just the dispatcher's own body, found by matching its braces."""
    at = source.find(DISPATCH)
    if at < 0:
        return None
    open_at = at + len(DISPATCH) - 1
    depth = 0
    for here in range(open_at, len(source)):
        if source[here] == "{":
            depth += 1
        elif source[here] == "}":
            depth -= 1
            if depth == 0:
                return source[open_at:here + 1]
    return None


def main() -> int:
    source = SCENE.read_text()
    body = dispatcher(source)
    if body is None:
        print("dev scene: the dispatcher's switch is gone, so nothing can be checked")
        return 1
    steps = sorted(int(match.group(1)) for match in CASE.finditer(body))
    problems = []

    if not steps:
        problems.append("no numbered steps at all - has the dispatcher moved?")
        steps = [0]

    if steps[0] != 0:
        problems.append(f"the scene starts at step {steps[0]}, not 0")

    for number in sorted(set(steps)):
        if steps.count(number) > 1:
            problems.append(f"step {number} is written {steps.count(number)} times")

    holes = [number for number in range(steps[0], steps[-1] + 1) if number not in steps]
    if holes:
        problems.append(
            "no case for step " + ", ".join(str(hole) for hole in holes)
            + " - the scene would stop there and call it done")

    declared = LAST.search(source)
    if declared is None:
        problems.append("LAST_STEP is gone, so nothing checks where the scene ends")
    elif int(declared.group(1)) != steps[-1]:
        problems.append(
            f"LAST_STEP says {declared.group(1)} but the last case is {steps[-1]}")

    problems.extend(budgetProblems(source))

    for problem in problems:
        print(f"dev scene: {problem}")
    print(f"\n{len(steps)} scene steps checked, {len(problems)} problems")
    return 1 if problems else 0


def budgetProblems(source):
    """Whether the scene's scripted waiting still fits the smallest budget shots.sh gives it."""
    if not SHOTS.is_file():
        return ["tools/shots.sh is gone, so nothing says how long the scene is allowed"]
    budgets = [int(found) for found in BUDGET.findall(SHOTS.read_text(encoding="utf-8"))]
    if not budgets:
        return ["tools/shots.sh no longer sets a budget this can be checked against"]

    named = {name: int(value) for name, value in CONSTANT.findall(source)}
    ticks = 0
    counted = 0
    for expression in list(ADVANCE.findall(source)) + list(WAITED.findall(source)):
        try:
            ticks += int(eval(expression.strip(), {"__builtins__": {}}, named))
            counted += 1
        except Exception:
            continue
    if counted == 0:
        return ["no scripted waiting found in the scene at all, which cannot be right"]

    seconds = ticks / A_SECOND
    room = min(budgets) * MOST_OF_IT
    if seconds > room:
        return [f"the scene waits {seconds:.0f}s on purpose, past the {room:.0f}s that leaves"
                f" room inside the smallest budget in tools/shots.sh ({min(budgets)}s)"]
    return []


if __name__ == "__main__":
    sys.exit(main())
