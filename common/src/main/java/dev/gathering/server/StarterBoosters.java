package dev.gathering.server;

import dev.gathering.core.card.MagicColor;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.ServerSettings;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two boosters for finishing the guided first game, one of each color they picked.
 * <p>The one thing in the guided first game that puts a real card into an economy. Everything
 * else about practice is sealed off from it on purpose - blank stock, nothing handed back,
 * nothing stakeable - and this is deliberately on the other side of that line, because
 * somebody who has just learned the controls needs something to use them on.
 * <p>Boosters rather than ready decks, and the difference is the point: a sealed pack is a
 * thing to open, and opening one is the other half of what this mod is. Two Jumpstart halves
 * shuffled together is a real deck; kept apart, they are a deck each for two people.
 * <p>Which color narrows <em>which</em> arrangements the seed chooses between and nothing
 * else. What is inside is still decided when the pack is torn, out of the published collation,
 * by a seed nobody has seen - see {@link PackComponent}.
 * <p><b>Once.</b> Written down in the save before the packs are handed over, and a player whose
 * name is on that list is told they have already had theirs. Redoing the tutorial is free and
 * always will be; being paid for redoing it is not.
 * <p>Server thread only.
 */
public final class StarterBoosters {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /**
     * Who has had theirs, inside the save like everything owed. Past its ceiling nothing more is given, which is
     * the safe direction: the failure is somebody not getting two boosters rather than everybody getting them again.
     */
    private static final SavedPlayerList GIVEN = new SavedPlayerList("starter", "given.txt",
            "# Players who have been given their two starter boosters. One id to a line.");

    private StarterBoosters() {
    }

    /** How the asking went, and what to tell the player. */
    public enum Outcome {

        /** They have their two packs. */
        GIVEN,

        /** They have had them before. */
        ALREADY,

        /** This server hands out no starter at all. */
        TURNED_OFF,

        /** They have not finished the guided first game on this world. */
        LESSON_NOT_FINISHED,

        /** The set they would come from is not one this server can open. */
        NO_SUCH_SET,

        /** Two different colors are needed and two different colors were not sent. */
        NOT_TWO_COLORS,

        /** Nothing could be written down, so nothing was handed over. */
        COULD_NOT_RECORD;

        public String messageKey() {
            return switch (this) {
                case GIVEN -> "message.gathering.starter_given";
                case ALREADY -> "message.gathering.starter_already";
                case TURNED_OFF -> "message.gathering.starter_off";
                case LESSON_NOT_FINISHED -> "message.gathering.starter_needs_lesson";
                case NOT_TWO_COLORS -> "message.gathering.starter_off";
                case NO_SUCH_SET, COULD_NOT_RECORD -> "message.gathering.starter_unavailable";
            };
        }
    }

    /**
     * Hands over two packs, once.
     * <p>Written down first and handed over second, which is the same order {@link Owed}
     * settled on for the same reason: a write that fails hands nothing over, and the player
     * asks again. Handing over first and recording afterwards would turn one failed write
     * into an unlimited supply of boosters.
     *
     * @param picked exactly two different colors. Two of the same is refused rather than
     *               quietly turned into one pack of each: it is not what any screen sends, so
     *               it is a client that has been edited, and the honest answer is no
     */
    public static Outcome give(ServerPlayer player, List<MagicColor> picked) {
        if (player == null || picked == null) {
            return Outcome.NOT_TWO_COLORS;
        }
        if (picked.size() != 2 || picked.get(0) == picked.get(1)) {
            return Outcome.NOT_TWO_COLORS;
        }
        var collecting = ServerSettings.get().collecting();
        if (!collecting.givesAStarter()) {
            return Outcome.TURNED_OFF;
        }
        String set = collecting.starterSet();
        String product = collecting.starterProduct();

        if (alreadyHad(player.getUUID())) {
            return Outcome.ALREADY;
        }
        // For finishing the lesson, which the server holds the client's word about to the shape of a lesson
        // really played: see LessonRecords.
        if (!LessonRecords.finished(player.getUUID())) {
            return Outcome.LESSON_NOT_FINISHED;
        }
        if (!GIVEN.add(player.getUUID())) {
            LOGGER.error("Could not write down that {} had their starter boosters, so they"
                    + " were not handed over", player.getUUID());
            return Outcome.COULD_NOT_RECORD;
        }

        for (MagicColor color : picked) {
            ItemStack pack = PackItem.of(new PackComponent(set, product, color.code()));
            if (pack.isEmpty()) {
                // A set code the config wrote that is not a set code at all. The line is
                // already written, which is the right way round: they can be given one by
                // hand, and nobody can farm it.
                LOGGER.warn("The starter set {} is not a set this server can make a pack of", set);
                return Outcome.NO_SUCH_SET;
            }
            Handing.give(player, pack);
        }
        LOGGER.info("Gave {} two starter boosters: {} and {}",
                player.getGameProfile().getName(), picked.get(0), picked.get(1));
        return Outcome.GIVEN;
    }

    /** Whether this player has already been given theirs. A list that cannot be read says yes: see below. */
    public static boolean alreadyHad(UUID player) {
        // A list that cannot be read is treated as "they have had theirs", which is the safe direction: the cost
        // is one player asking an operator, and the cost of the other answer is everybody on the server being
        // given two boosters a minute.
        return player == null || GIVEN.contains(player, true);
    }

    /**
     * Forgets one player, so they may be given theirs again.
     * <p>For an operator putting right a starter that went wrong, and for the tests. Not
     * reachable from anything a player can do.
     */
    public static boolean forget(UUID player) {
        return GIVEN.remove(player);
    }

    /**
     * Asks each player may make. Every ask reads the list from disk on the server thread, so a
     * client sending them in a loop was a disk read and a chat line a tick.
     */
    private static final ActionBudget ASKS = new ActionBudget(0.5, 2);

    /** Forgets every player's asks, for a server that is stopping. The list itself is on disk. */
    public static void clear() {
        ASKS.clear();
    }

    /**
     * Answers a client that has picked its two colors.
     * <p>The colors are letters on the wire and are looked up here, so a client cannot send a
     * sixth color, and nothing a client sends decides which cards come out - it decides which
     * ninth of the product the seed picks from, and the seed is the server's.
     */
    public static void handle(ServerPlayer player, dev.gathering.network.StarterPayload asked) {
        if (player == null || asked == null || !ASKS.spend(player.getUUID(), 1)) {
            return;
        }
        List<MagicColor> picked = asked.colors().stream()
                .map(MagicColor::of)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .toList();
        Outcome how = give(player, picked);
        player.sendSystemMessage(Component.translatable(how.messageKey()));
        if (how == Outcome.GIVEN) {
            Achievements.award(player, Achievements.FIRST_DECK);
        }
    }

    /** What this server would hand out, for a screen that wants to say so. Lower case. */
    public static String setCode() {
        return ServerSettings.get().collecting().starterSet().toLowerCase(Locale.ROOT);
    }
}
