package dev.gathering.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/**
 * Work that has to happen on a later tick, and the reason this class exists at all.
 * <p>Two throttles in this mod wanted the same thing: a request that arrives too soon should
 * wait its turn rather than be thrown away. Both were written as "put it back on
 * {@code server.execute} and look again", on the belief that {@code execute} queues a task for
 * the next tick.
 * <p>It does not. {@code BlockableEventLoop.scheduleExecutables()} is {@code !isSameThread()},
 * so a call made <em>on</em> the server thread runs the task inline, immediately, before
 * returning - the tick cannot advance inside it. Both drains therefore re-entered themselves
 * without limit: an audit sent two collection searches in one tick and got a
 * {@code StackOverflowError}. {@code MinecraftServer} narrows it further, to
 * {@code super && !isStopped()}, so once a server has stopped even a worker thread's
 * {@code execute} runs the task inline rather than dropping it - which was the other thing
 * assumed here, and the reason a static check had to be tightened alongside this.
 * <p>So: a real tick hook, called by both loaders, and work that names the tick it may run on.
 * One entry per key, replaced rather than queued - three keystrokes while a throttle is closed
 * are three searches nobody wants two of.
 * <p>Server thread only.
 */
public final class ServerTicks {

    /** Something waiting, and the first tick it may go on. */
    private record Waiting(int notBefore, Runnable what) {
    }

    /**
     * What is waiting, by key. A key is whatever the caller uses to mean "this one thing" -
     * a player's id for their pending search, a pair for their pending replay frame.
     */
    private static final Map<Object, Waiting> WAITING = new LinkedHashMap<>();

    /**
     * A ceiling, so a server that has gone wrong cannot grow this without end.
     * <p>Every key in here belongs to a connected player and is replaced rather than added to,
     * so in practice this is bounded by the player count several times over.
     */
    private static final int MOST_WAITING = 4096;

    private ServerTicks() {
    }

    /**
     * Runs this on a tick at or after {@code notBefore}, replacing whatever was waiting under
     * the same key.
     * <p>Never runs it here, even when the tick has already arrived: a caller that wanted it
     * now would not have called this, and running inline is the thing that went wrong.
     */
    public static void on(Object key, int notBefore, Runnable what) {
        if (key == null || what == null) {
            return;
        }
        if (!WAITING.containsKey(key) && WAITING.size() >= MOST_WAITING) {
            return;
        }
        WAITING.put(key, new Waiting(notBefore, what));
    }

    /** Drops whatever is waiting under this key, for a disconnect or a change of mind. */
    public static void forget(Object key) {
        WAITING.remove(key);
    }

    /** Drops everything, for a server that is stopping. */
    public static void clear() {
        WAITING.clear();
    }

    /** What is waiting right now, which is what a test asks. */
    public static int waiting() {
        return WAITING.size();
    }

    /**
     * One server tick. Called from both loaders' tick hooks and nowhere else.
     * <p>Everything due is taken out of the map <em>before</em> any of it runs, so a task that
     * queues itself again lands on a later tick rather than on this one. That is the whole
     * point of the class and it is one line: the previous shape ran the retry inside the
     * retry.
     */
    public static void tick(MinecraftServer server) {
        if (server == null || WAITING.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        List<Runnable> due = new ArrayList<>();
        WAITING.entrySet().removeIf(entry -> {
            if (entry.getValue().notBefore() > now) {
                return false;
            }
            due.add(entry.getValue().what());
            return true;
        });
        for (Runnable what : due) {
            try {
                what.run();
            } catch (RuntimeException wentWrong) {
                // One player's dropped search must not stop another player's. There is nothing
                // to be done about it here beyond not letting it out.
                org.slf4j.LoggerFactory.getLogger("Gathering")
                        .warn("Something waiting for a tick failed: {}", wentWrong.toString());
            }
        }
    }
}
