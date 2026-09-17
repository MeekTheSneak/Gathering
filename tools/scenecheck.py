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

CONSTANT = re.compile(r"private static final int ([A-Z_]+) = ([^;]+);")
#: A scene step moving on, and one waiting where it is. Named calls only, so a pack cloth's
#: own advance(1f / 60f) - a simulation step, not a pause - is not counted as scene time.
ADVANCE = re.compile(r"(?<![.\w])advance\(([^()]*)\)")
WAIT_HERE = re.compile(r"(?<![.\w])waitHere\(([^()]*)\)")
WAITED = re.compile(r"waited = ([^;]+);")

#: What advance() and waitHere() call their own argument, so their bodies are not read as waits.
PASSED_ON = {"settle", "ticksToWait"}
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
    problems.extend(stuckFailures(source))

    for problem in problems:
        print(f"dev scene: {problem}")
    print(f"\n{len(steps)} scene steps checked, {len(problems)} problems")
    return 1 if problems else 0


def stuckFailures(source):
    """Steps that fail and return without moving on.

    A step that fails and returns is run again on the next tick, and the next, until the stuck
    clock gives up forty seconds later: the failure is logged some eight hundred times, whatever
    the step did before it failed is done again each tick, and a dozen of them end the tour. A step
    that fails moves on - advance, waitHere or finish - unless it means to wait.
    """
    start = source.find(DISPATCH)
    end = source.find("            default -> {", start)
    if start < 0 or end < 0:
        return ["the scene's dispatcher could not be found to check its failures"]
    region = source[start:end].split("\n")
    first = source[:start].count("\n") + 1
    problems = []
    for index, line in enumerate(region):
        if line.strip() != "return;":
            continue
        back = index - 1
        failed = False
        moved = False
        while back >= 0 and index - back <= 10:
            said = region[back].strip()
            if "fail(" in said:
                failed = True
            if "advance(" in said or "finish(" in said or "waitHere(" in said:
                moved = True
            if said.startswith("case ") or said.endswith("-> {") or (said.startswith("if (") and said.endswith("{")):
                break
            back -= 1
        if failed and not moved:
            problems.append(f"DevScene.java:{first + index} fails and returns without moving on, so the step "
                            "runs again every tick; advance, waitHere or finish before returning")
    return problems


def budgetProblems(source):
    """Whether the scene's scripted waiting still fits the smallest budget shots.sh gives it."""
    if not SHOTS.is_file():
        return ["tools/shots.sh is gone, so nothing says how long the scene is allowed"]
    budgets = [int(found) for found in BUDGET.findall(SHOTS.read_text(encoding="utf-8"))]
    if not budgets:
        return ["tools/shots.sh no longer sets a budget this can be checked against"]

    # Constants may be written as sums of others - STUCK_TICKS is 20 * 40 - so they are worked out
    # in passes until nothing more resolves.
    written = dict(CONSTANT.findall(source))
    named = {}
    for _ in range(4):
        for name, expression in written.items():
            if name in named:
                continue
            try:
                named[name] = int(eval(expression, {"__builtins__": {}}, dict(named)))
            except Exception:
                continue
    ticks = 0
    counted = 0
    unreadable = []
    waits = (list(ADVANCE.findall(source)) + list(WAIT_HERE.findall(source))
             + list(WAITED.findall(source)))
    for expression in waits:
        written = expression.strip()
        try:
            ticks += int(eval(written, {"__builtins__": {}}, named))
            counted += 1
        except Exception:
            # A wait this cannot size is time it would otherwise leave out without saying so.
            # The two helpers' own declarations and the parameters they pass on are not waits;
            # they are counted where the helpers are called.
            if not written.startswith("int ") and written not in PASSED_ON:
                unreadable.append(written)
    if counted == 0:
        return ["no scripted waiting found in the scene at all, which cannot be right"]
    if unreadable:
        return ["the scene waits for " + ", ".join(sorted(set(unreadable)))
                + ", which this cannot add up; write it from the scene's int constants"]

    seconds = ticks / A_SECOND
    # And the worst a run is allowed to spend on steps that stopped moving before it gives up. A run
    # with its full share of stuck steps is still a run that should finish and report, rather than be
    # killed by the timer with nothing saying which steps were stuck.
    stuck_ticks = named.get("STUCK_TICKS")
    most_skipped = named.get("MOST_SKIPPED_STEPS")
    if stuck_ticks is None or most_skipped is None:
        return ["STUCK_TICKS or MOST_SKIPPED_STEPS is gone, so the worst case of stuck steps cannot be counted"]
    stuck = stuck_ticks * most_skipped / A_SECOND
    room = min(budgets) * MOST_OF_IT
    if seconds + stuck > room:
        return [f"the scene waits {seconds:.0f}s on purpose and may spend {stuck:.0f}s on stuck steps, past"
                f" the {room:.0f}s that leaves room inside the smallest budget in tools/shots.sh ({min(budgets)}s)"]
    return []


if __name__ == "__main__":
    sys.exit(main())
