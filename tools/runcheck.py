"""Every asynchronous result knows which world asked for it.

The card and collation pipelines run on their own threads and their results land whenever they
land. In single-player, leaving to the main menu and opening another world happens inside one
process - so a read started by world A can finish while world B is running, and publish A's
answer into B's shop, B's loot table or B's archive. The loaders made it worse by clearing the
shared state before closing the executors, so there was a window where a worker wrote into
state that had just been emptied.

There are exactly two things that know which world a result belongs to:

  * `ServerRun.stillThisRun(...)`, which stamps the generation when the work starts and drops
    the result if that world has since gone; and
  * `player.server.execute(...)`, which queues onto the asking server's own task queue - a
    queue that is never drained again once that server stops, so the work simply never runs.

So every completion in the server and service packages has to go through one of them. A third
way is not forbidden because it is wrong in principle; it is forbidden because whoever writes
it next will not think about this, and the failure is invisible until somebody's shop is
selling another world's sets.

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

#: The things that know which world asked. The wrapper is the usual one; the bare check is
#: for a completion with tidying-up of its own that has to happen either way - the shop's
#: restock flag has to be put back down even when the answer is thrown away.
BOUND = (
    "ServerRun.stillThisRun(",
    "ServerRun.isStill(",
    "player.server.execute(",
    "server.execute(",
)

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
                if not any(bound in window for bound in BOUND):
                    problems.append(
                        f"{path.relative_to(ROOT)}:{number} takes an asynchronous result "
                        f"without saying which world asked for it. Wrap it in "
                        f"ServerRun.stillThisRun(...) or hop through player.server.execute(...)."
                    )

    if problems:
        for problem in problems:
            print(problem)
        print(f"\n{len(problems)} completion(s) that could publish into the next world")
        return 1

    print(f"{checked} asynchronous completion(s) checked, all bound to the world that asked")
    return 0


if __name__ == "__main__":
    sys.exit(main())
