package dev.gathering.server;

import dev.gathering.core.tutorial.TutorialStep;
import dev.gathering.network.LessonPayload;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who has finished the guided first game on this world, which is what earns the starter boosters.
 * <p>The owner's rule: the packs are for finishing the lesson. The lesson is played on a board the player's own
 * client builds, with cards it made up, so the server never sees a move of it and cannot watch it finished. What
 * it can hold a client to is the shape of finishing: the lesson said it began, at least {@link #FEWEST_SECONDS}
 * seconds before it said it finished, and every step was done. A finish with no beginning, too soon after one, or
 * with a step missing is not written down. That is what the unmodified client does and what a stray or replayed
 * request cannot; a client rewritten to lie about a lesson it never played is not something any check made from
 * here could tell apart, and it still gets the two packs only once.
 * <p>Written in the save, once, like the starter list beside it.
 */
public final class LessonRecords {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** How long the six steps take at the least: drawing, playing, tapping, counting, reading and passing. */
    public static final int FEWEST_SECONDS = 10;

    private static final SavedPlayerList FINISHED = new SavedPlayerList("starter", "lesson_finished.txt",
            "# Players who have finished the guided first game on this world. One id to a line.");

    /** When each player's lesson began, in server ticks. */
    private static final Map<UUID, Long> BEGAN = new HashMap<>();

    /** The server's tick, which a test may move on. */
    public static java.util.function.LongSupplier clock = () -> ServerRun.server().map(server -> (long) server.getTickCount()).orElse(0L);

    private LessonRecords() {
    }

    public static void handle(ServerPlayer player, LessonPayload told) {
        if (player == null || told == null) {
            return;
        }
        UUID id = player.getUUID();
        if (!told.finished()) {
            if (BEGAN.size() > 4096) {
                BEGAN.clear();
            }
            BEGAN.put(id, clock.getAsLong());
            return;
        }
        Long began = BEGAN.remove(id);
        if (began == null) {
            LOGGER.info("{} said they finished the lesson without it having begun; not written down",
                    player.getGameProfile().getName());
            return;
        }
        if (clock.getAsLong() - began < FEWEST_SECONDS * 20L) {
            LOGGER.info("{} said they finished the lesson {} ticks after it began; not written down",
                    player.getGameProfile().getName(), clock.getAsLong() - began);
            return;
        }
        List<String> every = Arrays.stream(TutorialStep.values()).map(Enum::name).toList();
        if (!told.steps().containsAll(every)) {
            return;
        }
        if (!finished(id) && !FINISHED.add(id)) {
            LOGGER.error("Could not write down that {} finished the lesson", player.getGameProfile().getName());
        }
    }

    /** Whether this player has finished the lesson on this world. A list that cannot be read says no. */
    public static boolean finished(UUID player) {
        return player != null && FINISHED.contains(player, false);
    }

    /** Forgets a lesson that had begun, for a player who has left. */
    public static void forget(UUID player) {
        BEGAN.remove(player);
    }

    /** Forgets every lesson that had begun, for a server that is stopping. The list itself is on disk. */
    public static void clear() {
        BEGAN.clear();
    }

    /** Writes a player down as having finished, for a test about what finishing earns. */
    public static boolean finishedForTesting(UUID player) {
        return finished(player) || FINISHED.add(player);
    }

    /** Takes a player off the finished list, for the tests and an operator putting something right. */
    public static boolean unfinishForTesting(UUID player) {
        return FINISHED.remove(player);
    }
}
