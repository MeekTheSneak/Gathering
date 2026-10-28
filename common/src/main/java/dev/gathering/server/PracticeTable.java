package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.PlayerRef;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;

/**
 * The practice table, retired - and the code that takes the leftovers apart.
 * <p><b>Nothing creates one of these any more.</b> The guided first game is a demonstration
 * built in the player's own client, at no table, with no session, no seat and nothing saved:
 * see {@link dev.gathering.client.TutorialDemo}. The network entry point below answers and
 * starts nothing, and no screen in the mod sends it.
 * <p>What is left here is {@link #retire}, which exists because old saves are not old code.
 * A world written before this change can contain a table marked practice, a game of invented
 * cards, a chair held by a name no player can have, and - in a save written before the intake
 * guard - a real deck somebody built, which the old ending would have discarded on its way
 * out. That last one is why this is a migration rather than a deletion.
 * <p>Creating one is gone from the jar too. The tests that need the legacy shape build it with
 * a fixture beside them, in the game-test source set, which is the only place it belongs.
 * <p>The description below is of the retired design, kept because it explains what the
 * leftovers in a save actually are.
 * <p>It was an ordinary session at an ordinary table - the same events, the same
 * authorization, the same view filtering - with exactly one thing different about it, which the
 * table records and everything that hands cards to a person checks.
 * <p><b>Nothing here can become property.</b> The deck is blank stock the server invents on the
 * spot: cards in the custom namespace with nothing printed on them, which no shop sells, no
 * pack contains and no cache has to be asked about. The table never takes the deck into its
 * keeping, so there is nothing to hand back when the game ends; and if a future change made it
 * hold one anyway, {@code TableSessions.giveBack} refuses to hand a practice deck to anybody.
 * Playing for keeps is refused outright, so nothing can be staked.
 * <p>Blank stock rather than real cards is also what makes this work on a fresh install with
 * an empty card cache and no network: there is nothing to look up. A player's first minute on
 * a server should not depend on Scryfall answering.
 * <p>The second seat is a demonstration, not an opponent. Nobody is in it and nothing plays
 * from it; it holds one face-up card so that "read a card somebody else has played" is a thing
 * that can be done alone. There is no AI here and there is not going to be.
 * <p>Server thread only.
 */
public final class PracticeTable {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    /**
     * Who sits in the demonstration seat: nobody, by a name nobody can have.
     * <p>Derived from a fixed string rather than random, so it is the same across restarts and
     * so a saved practice game reopens with its seat still taken. It is not a player: no
     * client is ever authorized as it, nothing is ever sent to it, and it cannot log in.
     */
    private static final UUID THE_DEMONSTRATION =
            UUID.nameUUIDFromBytes("gathering:practice-demonstration".getBytes(StandardCharsets.UTF_8));

    private PracticeTable() {
    }

    /**
     * Takes apart a practice game left in a save by the design this replaced.
     * <p>Old worlds can hold one: a table marked practice, a session of invented cards, a
     * demonstration seat nobody can leave, and - in a save written before the intake guard
     * landed - <b>a real deck somebody built</b>, taken by the table and due to be discarded
     * the moment that game ended. That last one is the reason this is not simply a flag being
     * cleared, and the reason it hands things back before it ends anything.
     *
     * <p>The order is the whole of the safety argument:
     *
     * <ol>
     *   <li>the practice flag comes off <b>first</b>, so that
     *   <li>ending the session runs the ordinary return path, which hands each held deck to
     *       the player who put it down - wherever they are - and puts it on the table, then on
     *       the floor, rather than nowhere. With the flag still on, that same path discards
     *       them, which is exactly the loss being repaired.
     * </ol>
     *
     * <p>Interrupted between the two, a crash leaves an ordinary table still holding the deck,
     * which the next ending hands back. Interrupted the other way round it would leave a deck
     * belonging to nothing, so it is not done the other way round.
     *
     * <p><b>Nothing is minted.</b> The invented cards live in the session and a session's
     * cards are not items; only a deck the table was <em>holding</em> comes back, and the only
     * thing that has ever put one there is a player committing a real one. Verified rather
     * than assumed: {@code holdDeck} has two production callers, the sideboard editor and the
     * commit path, and practice deals straight into the session without touching either.
     *
     * <p>Idempotent, because the flag it keys on is the first thing it clears. Running it
     * twice does nothing the second time, and it is called from a tick, so it will be.
     *
     * @return whether there was a practice game here to take apart
     */
    public static boolean retire(ServerLevel level, BlockPos tableOrigin, TableBlockEntity table) {
        if (level == null || tableOrigin == null || table == null || !table.isPractice()) {
            return false;
        }
        int holding = table.heldDecks().size();
        LOGGER.info("Retiring a practice game left at {} by the old guided first game;"
                + " {} held deck(s) go back to whoever put them down", tableOrigin, holding);

        // First, so that the ending below hands decks back instead of discarding them.
        table.stopBeingPractice();

        if (table.hasSession()) {
            TableSessions.end(level, tableOrigin, null, "practice retired");
        }
        // The seat nobody was ever in. Left behind it is a chair at a real table that no
        // player can sit in and no player can be asked to get out of.
        release(level, tableOrigin, THE_DEMONSTRATION);
        TableBroadcast.sendToTable(level, tableOrigin);
        return true;
    }

    /** Whether the game at this table is somebody learning the controls. */
    public static boolean isPracticeAt(BlockGetter level, BlockPos tableOrigin) {
        return TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .map(TableBlockEntity::isPractice)
                .orElse(false);
    }

    /** The seat nobody is in, for anything that needs to tell it apart from a person. */
    public static UUID demonstrationSeat() {
        return THE_DEMONSTRATION;
    }

    /**
     * Answers a client asking to start or stop practicing.
     * <p>The reach check is the same one every other position a client names goes through.
     * Everything else about a practice game is decided here rather than sent: which cards,
     * which seat and how many are the server's answers, so a client that skipped the button
     * gets exactly what the button would have got.
     */
    public static void handle(ServerPlayer player, dev.gathering.network.PracticePayload asked) {
        if (player == null || asked == null || asked.table() == null) {
            return;
        }
        if (!TableReach.within(player, asked.table())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (asked.what()) {
            case START -> {
                // Retired. The guided first game is a local demonstration now - it never asks
                // a server for anything, so nothing legitimate sends this any more. Closed
                // here rather than only in the screens: taking the button away leaves the
                // intake and lifecycle behind it reachable by anything that still knows the
                // packet, which is the whole of what "retire it safely" was asked for.
                //
                // Answered rather than dropped, because a client from an older version will
                // send this and deserves to be told why nothing happened.
                player.sendSystemMessage(
                        Component.translatable("message.gathering.practice_retired"));
            }
            case STOP -> {
                // Only their own practice game, and only if it is one. A client asking to stop
                // a game somebody else is really playing is asking for nothing to happen.
                //
                // Through retire, never through the old ending. Nothing creates a practice table
                // now, so any this reaches is a leftover in an old save - and the table's first
                // tick retires those, but an old client can send STOP before that tick runs. The
                // old ending discarded whatever the table was holding, on the assumption that it
                // only ever held invented cards; in a save written before the intake guard that is
                // a real deck somebody built. Reproduced: zero copies left, expected one.
                // retire takes the flag off first, so the ordinary return hands it back.
                if (isPracticeAt(level, asked.table())
                        && TableSessions.seatIdOf(level, asked.table(), player.getUUID()).isPresent()) {
                    TableSessions.anchorOf(level, asked.table())
                            .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                            .ifPresent(table -> retire(level, asked.table(), table));
                }
            }
        }
    }

    // ------------------------------------------------------------------ bits

    /** Empties the demonstration seat, which no ordinary path would ever be asked to do. */
    private static void release(ServerLevel level, BlockPos tableOrigin, UUID who) {
        TableSeats.leave(level, tableOrigin, who);
    }

    /**
     * A name for the demonstration seat, so the board says something rather than nothing.
     * <p>Not a player and never pretending to be one: the log and the seat label both read as
     * a demonstration, which is what it is.
     */
    public static PlayerRef demonstrationRef() {
        return new PlayerRef(THE_DEMONSTRATION,
                Component.translatable("tutorial.gathering.demonstration_seat").getString());
    }
}
