"""Every asynchronous result knows which world asked for it.

The card and collation pipelines run on their own threads and their results land whenever they
land. In single-player, leaving to the main menu and opening another world happens inside one
process - so a read started by world A can finish while world B is running, and publish A's
answer into B's shop, B's loot table or B's archive. The loaders made it worse by clearing the
shared state before closing the executors, so there was a window where a worker wrote into
state that had just been emptied.

The second half of this file used to say `player.server.execute(...)` was proof of that,
because a stopped server's queue is never drained. **That is false**, and an audit was right to
say so. `MinecraftServer.scheduleExecutables()` is `super.scheduleExecutables() && !isStopped()`
and the base is `!isSameThread()`, so `BlockableEventLoop.execute` behaves like this:

  * called on the server thread, it runs the task **inline**, immediately - it is not a
    next-tick scheduler and never was; and
  * called on any thread once the server has **stopped**, it also runs the task inline, on the
    caller's thread - so a card-worker completion lands on the card worker, reaching into
    whatever world is running by then.

So there is exactly one thing that knows which world a result belongs to, and it is the one
that checks:

  * `ServerRun.stillThisRun(...)` and `ServerRun.isStill(...)`, which stamp the generation when
    the work starts and drop the result if that world has since gone; and
  * `ServerRun.onServerThread(...)` / `onTheServerThread(...)` / `laterOnTheServerThread(...)`,
    which do the same and then hand the task to the server it was started on, checking again
    inside in case it stopped in between.

A bare `server.execute` is no longer accepted. Not because it is wrong everywhere, but because
whoever writes the next one will not think about any of the above, and the failure is invisible
until somebody's shop is selling another world's sets.

Run it from the repo root:  python3 tools/runcheck.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

WHERE = [
    "common/src/main/java/dev/gathering/server",
    "common/src/main/java/dev/gathering/service",
    "common/src/main/java/dev/gathering/block",
    "common/src/main/java/dev/gathering/village",
]

#: A result landing from another thread.
LANDS = re.compile(r"\.(?:whenComplete|thenAccept|thenRun)\s*\(")

#: The things that know which world asked. The wrappers are the usual ones; the bare check is
#: for a completion with tidying-up of its own that has to happen either way - the shop's
#: restock flag has to be put back down even when the answer is thrown away.
BOUND = (
    "ServerRun.stillThisRun(",
    "ServerRun.isStill(",
    "ServerRun.onServerThread(",
    "ServerRun.onTheServerThread(",
    "ServerRun.laterOnTheServerThread(",
)

#: Named so the message can say why, rather than only that something is missing.
NOT_A_FENCE = ("server.execute(",)

#: A completion whose body is deliberately not fenced says so on one of its own lines, with a
#: reason. The same shape statecheck uses for a holder that should genuinely survive: being
#: written down is what this looks for, and the reason is for whoever reads it next.
EXCUSED = "runcheck:"

#: Where the two are defined, and the one call that has no world to belong to.
EXEMPT = {
    "ServerRun",
    # Warms its own cache before anything can read it, and logs a count. Nothing published.
    "CardDataService",
}


def main():
    problems = []
    checked = 0
    for folder in WHERE:
        here = ROOT / folder
        if not here.is_dir():
            continue
        for path in sorted(here.rglob("*.java")):
            if path.stem in EXEMPT:
                continue
            lines = path.read_text(encoding="utf-8").splitlines()
            for number, line in enumerate(lines, start=1):
                if not LANDS.search(line) or line.lstrip().startswith(("*", "//")):
                    continue
                checked += 1
                # The binding may be on this line or on the next few, which is where a
                # formatter puts it when the lambda is long.
                window = "\n".join(lines[number - 1:number + 10])
                if any(bound in window for bound in BOUND) or EXCUSED in window:
                    continue
                why = ("server.execute is not a fence: on the server thread it runs the task "
                       "inline, and once the server has stopped it runs it inline on the "
                       "calling thread. ") if any(
                    loose in window for loose in NOT_A_FENCE) else ""
                problems.append(
                    f"{path.relative_to(ROOT)}:{number} takes an asynchronous result "
                    f"without saying which world asked for it. {why}"
                    f"Wrap it in ServerRun.onServerThread(...) or ServerRun.stillThisRun(...)."
                )

    if problems:
        for problem in problems:
            print(problem)
        print(f"\n{len(problems)} completion(s) that could publish into the next world")
        return 1

    excused = sum(
        1
        for folder in WHERE
        if (ROOT / folder).is_dir()
        for path in (ROOT / folder).rglob("*.java")
        for line in path.read_text(encoding="utf-8").splitlines()
        if EXCUSED in line
    )
    print(f"{checked} asynchronous completion(s) checked, all bound to the world that asked"
          + (f" ({excused} excused in writing)" if excused else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
