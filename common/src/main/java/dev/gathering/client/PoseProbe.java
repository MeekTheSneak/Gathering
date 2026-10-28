package dev.gathering.client;

import dev.gathering.core.ui.TablePose;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A running commentary on what a seated body is being told to do, for working out why it is not
 * doing it.
 * <p>Off unless {@code -Dgathering.posedebug=1} is set, and off in every shipped jar, because
 * nothing switches it on. It exists because the alternative was guessing: the arm not moving could
 * be a pointer that never left the sender, a pointer the server dropped, a pointer that arrived
 * and was filed under the wrong table, or a pose worked out correctly and mapped onto the model
 * backwards, and those four look identical from a chair.
 * <p>Throttled, because the thing being watched runs sixty times a second per player.
 */
public final class PoseProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /**
     * Whether to say anything at all. Read once: this is asked from inside a render.
     * <p>Present, not {@code true}: {@code Boolean.getBoolean} accepts only the literal word, and
     * every other switch in this project is set to {@code 1} - so the first version of this was a
     * probe that ran, cost nothing, and said nothing, which is worse than no probe at all. Tested
     * the same way {@code DevScene} tests its own.
     */
    private static final boolean ON = System.getProperty("gathering.posedebug") != null;

    /** How long between lines, in milliseconds, so a frame loop does not fill the log. */
    private static final long EVERY = 700;

    private static long lastPose;

    private static long lastPointer;

    private PoseProbe() {
    }

    /** A pointer arrived off the wire. */
    public static void sawPointer(dev.gathering.network.TablePointingPayload payload) {
        if (!ON || quiet(lastPointer)) {
            return;
        }
        lastPointer = System.currentTimeMillis();
        LOGGER.info("posedebug: pointer from {} at {} on {},{}",
                payload.player(), payload.table(), payload.surfaceX(), payload.surfaceY());
    }

    /**
     * The model hook fired at all, whatever it decided to do.
     * <p>The first thing to know and the one the other lines cannot tell you: an {@code @Inject}
     * that matches nothing is silent by design here - {@code require = 0}, so a pack with another
     * mod in it still starts - and a hook that never fires looks exactly like a hook that fires
     * and poses nothing.
     */
    public static void sawHook(Object entity) {
        if (!ON || quiet(lastHook)) {
            return;
        }
        lastHook = System.currentTimeMillis();
        LOGGER.info("posedebug: hook fired for {}", entity == null ? "null" : entity.getClass().getSimpleName());
    }

    private static long lastHook;

    /** A body was posed this frame. */
    public static void sawPose(Player player, TablePose.Aim aim) {
        if (!ON || quiet(lastPose)) {
            return;
        }
        lastPose = System.currentTimeMillis();
        LOGGER.info("posedebug: {} bodyYaw={} aim yaw={} pitch={} headYaw={} headPitch={} seated={}",
                player.getGameProfile().getName(), Math.round(player.yBodyRot),
                Math.round(aim.armYaw()), Math.round(aim.armPitch()),
                Math.round(aim.headYaw()), Math.round(aim.headPitch()),
                SeatedPlayers.of(player.getUUID()).map(seat -> seat.side() + "@" + seat.table())
                        .orElse("no"));
    }

    private static boolean quiet(long since) {
        return System.currentTimeMillis() - since < EVERY;
    }
}
