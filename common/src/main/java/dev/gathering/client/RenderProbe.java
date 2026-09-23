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
 */
public final class RenderProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** Read once: this is asked from inside a render. Present, not {@code true} - see PoseProbe. */
    private static final boolean ON = System.getProperty("gathering.renderdebug") != null;

    /** How long between lines, in milliseconds. */
    private static final long EVERY = 3000;

    private static final Map<String, long[]> PARTS = new LinkedHashMap<>();

    private static long spokeAt;

    private static int frames;

    private RenderProbe() {
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

    /**
     * Called once at the end of a drawn frame.
     * <p>Says what it has when enough time has passed, and starts counting again - so what comes
     * out is the last few seconds rather than everything since the world loaded, and a player who
     * zooms in halfway through sees the difference in the next line rather than buried in an
     * average with the frames before it.
     */
    public static void frame(int cardsDrawn, int cardsSkipped) {
        if (!ON) {
            return;
        }
        frames++;
        long now = System.currentTimeMillis();
        if (now - spokeAt < EVERY) {
            return;
        }
        StringBuilder said = new StringBuilder("renderdebug: ")
                .append(frames).append(" frames, ")
                .append(cardsDrawn).append(" cards drawn, ")
                .append(cardsSkipped).append(" off screen");
        PARTS.forEach((name, sum) -> said.append(" | ").append(name).append(' ')
                .append(Math.round(sum[0] / 1_000_000.0 / Math.max(1, frames) * 100.0) / 100.0)
                .append("ms/frame"));
        LOGGER.info(said.toString());
        PARTS.clear();
        frames = 0;
        spokeAt = now;
    }
}
