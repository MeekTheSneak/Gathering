package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.server.ServerTicks;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tick hook the throttles wait on.
 * <p>This exists because the thing it replaced looked correct and was not.
 * {@code server.execute} was used as "run this next tick", on the strength of the name; in
 * 1.21.1 {@code BlockableEventLoop.scheduleExecutables()} is {@code !isSameThread()}, so a
 * call made on the server thread runs the task <em>inline</em> and the tick cannot advance
 * inside it. Two throttled requests in one tick therefore recursed until the stack ran out.
 * <p>So the two properties that fix depends on are asserted here rather than left to read
 * correctly: work never runs in the call that queues it, and work that queues itself again
 * lands on a later tick instead of on this one.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerTicksGameTest {

    /** Queuing must never run the work, even when the tick it named has already been and gone. */
    @GameTest(template = "empty")
    public static void queuingNeverRunsTheWorkInline(GameTestHelper helper) {
        Object key = "gathering-test:inline";
        ServerTicks.forget(key);
        AtomicInteger ran = new AtomicInteger();
        try {
            // A tick long past. The old shape would have run this here, on the spot.
            ServerTicks.on(key, helper.getLevel().getServer().getTickCount() - 100, ran::incrementAndGet);
            if (ran.get() != 0) {
                helper.fail("queuing work for a tick already gone ran it inline");
                return;
            }
            if (ServerTicks.waiting() < 1) {
                helper.fail("work queued for a tick already gone was dropped rather than waiting");
                return;
            }
        } finally {
            ServerTicks.forget(key);
        }
        helper.succeed();
    }

    /** One key, one entry: a burst of requests is one piece of work, not a queue of them. */
    @GameTest(template = "empty")
    public static void oneKeyKeepsOnlyTheNewest(GameTestHelper helper) {
        Object key = "gathering-test:newest";
        ServerTicks.forget(key);
        int before = ServerTicks.waiting();
        AtomicInteger which = new AtomicInteger();
        try {
            int soon = helper.getLevel().getServer().getTickCount() + 1;
            for (int attempt = 1; attempt <= 5; attempt++) {
                int mine = attempt;
                ServerTicks.on(key, soon, () -> which.set(mine));
            }
            if (ServerTicks.waiting() != before + 1) {
                helper.fail("five requests under one key left "
                        + (ServerTicks.waiting() - before) + " pieces of work waiting");
                return;
            }
        } catch (RuntimeException wentWrong) {
            ServerTicks.forget(key);
            throw wentWrong;
        }
        helper.runAfterDelay(3, () -> {
            if (which.get() != 5) {
                ServerTicks.forget(key);
                helper.fail("the tick ran request " + which.get() + " rather than the newest");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Work that queues itself again must land on a later tick.
     * <p>The property the {@code StackOverflowError} was the absence of. A task is drained,
     * re-queues itself for the tick it is already on, and must not be run again inside the
     * same drain - which is why everything due comes out of the map before any of it runs.
     */
    @GameTest(template = "empty")
    public static void workThatRequeuesItselfWaitsForAnotherTick(GameTestHelper helper) {
        Object key = "gathering-test:requeue";
        ServerTicks.forget(key);
        AtomicInteger runs = new AtomicInteger();
        int soon = helper.getLevel().getServer().getTickCount() + 1;
        ServerTicks.on(key, soon, new Runnable() {
            @Override
            public void run() {
                // Three times, each on its own tick. A fourth would mean a drain ran a task
                // it had just been handed, which is the recursion this class exists to stop.
                if (runs.incrementAndGet() < 3) {
                    ServerTicks.on(key, soon, this);
                }
            }
        });
        helper.runAfterDelay(2, () -> {
            if (runs.get() != 1) {
                ServerTicks.forget(key);
                helper.fail("two ticks in, work that re-queues itself had run "
                        + runs.get() + " times rather than once");
                return;
            }
            helper.runAfterDelay(4, () -> {
                ServerTicks.forget(key);
                if (runs.get() != 3) {
                    helper.fail("six ticks in, work that re-queues itself twice had run "
                            + runs.get() + " times rather than three");
                    return;
                }
                helper.succeed();
            });
        });
    }
}
