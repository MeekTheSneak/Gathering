package dev.gathering.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where a frame of the board actually goes, for working out why one is slow.
 * <p>Off unless {@code -Drenderdebug=1} is set, and off in every shipped jar, because nothing
 * switches it on. It exists because the last time this project guessed at a performance problem it
 * added an off-screen cull, wrote the complaint into the comment beside it, and the complaint came
 * back unchanged - which is what guessing at a cost rather than measuring one buys.
 * <p>Sums the time in each named part of the frame and says so every few seconds, with the frame
 * count, so the number that matters is a share of a frame rather than a stopwatch reading somebody
 * took once. Nothing here is a benchmark: it runs inside a real client drawing a real board, which
 * is the only place the answer is the true one.
 * <p><b>Times, counts and garbage.</b> A part is timed with {@link #start} and {@link #lap}; a
 * number of things a frame did - draw calls, textures uploaded, layouts worked out - is added with
 * {@link #count}; and the bytes a part allocated, where the JVM says, with {@link #allocated}. Each
 * comes out per frame. Under software GL the frame rate is mostly the cost of filling pixels, which
 * a real graphics card pays a hundredth of, so the counts and the CPU time of each part are the
 * numbers worth comparing, not the frames.
 * <p><b>Who counts the frames.</b> The development client's frame hook - see
 * {@code RenderProbeFrames} in NeoForge's development sources - says when a frame begins and ends,
 * and when the world inside it does, so a frame with no table screen open is counted too. Without
 * it, the table screen counts its own frames, which is all this did before.
 * <p>Client-only, render thread only.
 */
public final class RenderProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** Read once: this is asked from inside a render. Present, not {@code true} - see PoseProbe. */
    private static final boolean ON = System.getProperty("gathering.renderdebug") != null;

    /** How long between lines, in milliseconds. */
    private static final long EVERY = 3000;

    // statecheck: a development probe that is off in every shipped jar, emptied every few seconds
    private static final Map<String, long[]> PARTS = new LinkedHashMap<>();

    // statecheck: the same probe's counts, emptied with its times
    private static final Map<String, long[]> COUNTS = new LinkedHashMap<>();

    private static long spokeAt;

    private static int frames;

    /** What the flat board drew in the last frame it drew, for the line. */
    private static int cardsDrawn;
    private static int cardsSkipped;

    /** Whether a frame hook is counting frames, so the table screen leaves it to the hook. */
    private static boolean hooked;

    private static long frameStarted;
    private static long worldStarted;

    /** Said once, the first time the JVM will not say how much a thread has allocated. */
    private static boolean noAllocationCount;

    private RenderProbe() {
    }

    /** Whether the probe is on at all: for a caller that would do work only to measure. */
    public static boolean on() {
        return ON;
    }

    /**
     * Where a frame's first timed part begins: the clock when the probe is on, and nothing at all
     * when it is off.
     * <p>A start and a lap rather than a method that takes the work as a lambda. The lambda version
     * was how this was first written, and it allocated one object per part per frame whether the
     * probe was on or not - in the one render path this probe exists to make cheaper.
     */
    public static long start() {
        return ON ? System.nanoTime() : 0L;
    }

    /**
     * Charges the time since {@code since} to this part, and returns now, for the next part to be
     * measured from. Does nothing, and reads no clock, when the probe is off.
     */
    public static long lap(String name, long since) {
        if (!ON) {
            return 0L;
        }
        long now = System.nanoTime();
        long[] sum = PARTS.computeIfAbsent(name, each -> new long[2]);
        sum[0] += now - since;
        sum[1]++;
        return now;
    }

    /** Adds this many to a count kept per frame - draw calls, uploads, layouts worked out. */
    public static void count(String name, long howMany) {
        if (!ON) {
            return;
        }
        COUNTS.computeIfAbsent(name, each -> new long[1])[0] += howMany;
    }

    /**
     * How many bytes this thread has allocated so far, as the JVM counts them; nothing when the
     * probe is off or the JVM will not say. Taken before and after a part, the difference is the
     * garbage that part made: see {@link #allocatedSince}.
     */
    public static long allocated() {
        if (!ON || noAllocationCount) {
            return 0L;
        }
        try {
            java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();
            if (threads instanceof com.sun.management.ThreadMXBean counting) {
                return counting.getCurrentThreadAllocatedBytes();
            }
        } catch (Throwable unavailable) {
            // Not every JVM can say, and a probe is no reason to stop a frame.
        }
        noAllocationCount = true;
        LOGGER.info("renderdebug: this JVM does not count what a thread allocates, so no garbage is reported");
        return 0L;
    }

    /** Charges the bytes allocated since {@code before} to this part, in kilobytes. */
    public static void allocatedSince(String name, long before) {
        if (!ON || noAllocationCount) {
            return;
        }
        long now = allocated();
        if (now > 0L && before > 0L) {
            count(name + " KB", (now - before) / 1024L);
        }
    }

    /** What the flat board drew this frame, and how many it left off the screen. */
    public static void cards(int drawn, int skipped) {
        cardsDrawn = drawn;
        cardsSkipped = skipped;
    }

    /** The frame hook: a frame begins. */
    public static void frameBegins() {
        if (!ON) {
            return;
        }
        hooked = true;
        frameStarted = System.nanoTime();
    }

    /** The frame hook: the world inside this frame begins to be drawn. */
    public static void worldBegins() {
        if (ON) {
            worldStarted = System.nanoTime();
        }
    }

    /** The frame hook: the world inside this frame has been drawn. */
    public static void worldEnds() {
        if (ON && worldStarted != 0L) {
            lap("world", worldStarted);
            worldStarted = 0L;
        }
    }

    /** The frame hook: a frame has been drawn. */
    public static void frameEnds() {
        if (!ON || frameStarted == 0L) {
            return;
        }
        lap("frame", frameStarted);
        frameStarted = 0L;
        frame();
    }

    /** The table screen has drawn itself: the end of a frame, unless a frame hook is counting them. */
    public static void screenDrawn() {
        if (ON && !hooked) {
            frame();
        }
    }

    /**
     * Starts counting again from nothing, so the next line is only what comes after this - for a
     * scripted run that has just changed what is on the screen.
     */
    public static void restart() {
        if (!ON) {
            return;
        }
        PARTS.clear();
        COUNTS.clear();
        frames = 0;
        spokeAt = System.currentTimeMillis();
    }

    /**
     * Called once at the end of a drawn frame.
     * <p>Says what it has when enough time has passed, and starts counting again - so what comes
     * out is the last few seconds rather than everything since the world loaded, and a player who
     * zooms in halfway through sees the difference in the next line rather than buried in an
     * average with the frames before it.
     */
    private static void frame() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - spokeAt < EVERY) {
            return;
        }
        StringBuilder said = new StringBuilder("renderdebug: ")
                .append(frames).append(" frames, ")
                .append(cardsDrawn).append(" cards drawn, ")
                .append(cardsSkipped).append(" off screen, ")
                .append(onTheScreen());
        PARTS.forEach((name, sum) -> said.append(" | ").append(name).append(' ')
                .append(Math.round(sum[0] / 1_000_000.0 / Math.max(1, frames) * 100.0) / 100.0)
                .append("ms/frame"));
        COUNTS.forEach((name, sum) -> said.append(" | ").append(name).append(' ')
                .append(Math.round(sum[0] / (double) Math.max(1, frames) * 10.0) / 10.0)
                .append("/frame"));
        LOGGER.info(said.toString());
        PARTS.clear();
        COUNTS.clear();
        frames = 0;
        spokeAt = now;
    }

    /** What was on the screen as the line was said, so a line can be told apart from the ones round it. */
    private static String onTheScreen() {
        net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen instanceof TableScreen table) {
            return table.board() instanceof dev.gathering.core.ui.SurfaceBoard ? "the board on the block" : "the flat board";
        }
        return screen == null ? "no screen" : screen.getClass().getSimpleName();
    }
}
