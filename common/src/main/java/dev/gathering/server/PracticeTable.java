package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.GlobalPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
 * <p>{@link #start} is kept, and is reachable from nothing in production. It is how the tests
 * build the shape a legacy save has, because the only honest way to check that a leftover is
 * taken apart correctly is to make a real one first. It is not a feature and must not be
 * wired to anything.
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

    /**
     * How many cards the practice library holds.
     * <p>Enough to draw from for as long as anybody wants to keep pressing the key, and few
     * enough that the board never looks like a real game somebody should be reading.
     */
    private static final int PRACTICE_LIBRARY = 20;

    private PracticeTable() {
    }

    /** Why a practice game could not start, or that it did. */
    public enum Outcome {

        /** It did. */
        STARTED,

        /** There is no table there. */
        NO_TABLE,

        /** A game is already going on here - somebody's real one, quite possibly. */
        GAME_RUNNING,

        /** Somebody else is sitting here. Practice is alone, so it does not take their table. */
        SOMEBODY_ELSE_HERE,

        /** This cluster has fewer than two seats, so there is nowhere to demonstrate from. */
        TOO_SMALL,

        /** The learner could not be seated, which should not happen and is said rather than hidden. */
        COULD_NOT_SIT;

        public String messageKey() {
            return "message.gathering.practice_" + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Starts a practice game for one player at this table. <b>Retired.</b>
     * <p>No production path reaches this. It is kept so that the migration tests can build the
     * thing a legacy save contains and then check it is taken apart properly - a test that
     * built the leftovers by hand would be testing its own idea of them. Do not wire it to a
     * screen, a payload or a command.
     * <p>Refuses rather than clearing anything out of the way, as it always did.
     */
    public static Outcome start(ServerPlayer learner, BlockPos tableOrigin) {
        if (learner == null || tableOrigin == null) {
            return Outcome.NO_TABLE;
        }
        ServerLevel level = learner.serverLevel();
        BlockPos anchor = TableSessions.anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null) {
            return Outcome.NO_TABLE;
        }
        if (table.hasSession()) {
            return Outcome.GAME_RUNNING;
        }

        List<SeatAnchor> seats = TableClusters.at(level, tableOrigin).seats();
        if (seats.size() < 2) {
            return Outcome.TOO_SMALL;
        }
        if (somebodyElseIsHere(level, tableOrigin, learner.getUUID())) {
            return Outcome.SOMEBODY_ELSE_HERE;
        }

        // The learner in the first free seat, the demonstration in the next one. Their own
        // seat first so that a player already sitting keeps the chair they chose.
        if (TableSeats.seatOf(level, tableOrigin, learner.getUUID()).isEmpty()
                && !sit(level, tableOrigin, seats, learner.getUUID())) {
            return Outcome.COULD_NOT_SIT;
        }
        if (!sit(level, tableOrigin, seats, THE_DEMONSTRATION)) {
            // Undo the seating rather than leaving them in a chair at a table with no game.
            TableSeats.leave(level, tableOrigin, learner.getUUID());
            return Outcome.TOO_SMALL;
        }

        // Commander, because that is what walking up to a table already starts, and a
        // tutorial that taught a different set of zones from the one waiting afterwards
        // would be teaching the wrong table.
        TableSessions.Outcome started = TableSessions.start(level, tableOrigin,
                new MatchRules(FormatPresets.COMMANDER, 1));
        if (started != TableSessions.Outcome.STARTED) {
            TableSeats.leave(level, tableOrigin, learner.getUUID());
            release(level, tableOrigin, THE_DEMONSTRATION);
            return Outcome.GAME_RUNNING;
        }

        // Marked before a single card is dealt. Everything that could hand a card to a person
        // asks this, so it has to be true before there is anything to hand.
        table.markAsPractice();

        GameSession session = table.session().orElse(null);
        SeatId mine = TableSessions.seatIdOf(level, tableOrigin, learner.getUUID()).orElse(null);
        SeatId theirs = TableSessions.seatIdOf(level, tableOrigin, THE_DEMONSTRATION).orElse(null);
        if (session == null || mine == null || theirs == null) {
            stop(level, tableOrigin);
            return Outcome.COULD_NOT_SIT;
        }

        deal(session, mine);
        deal(session, theirs);
        // One face-up card in front of the demonstration seat, so that "read a card somebody
        // else has played" can be done by somebody sitting alone. Written rather than blank,
        // because the step is about reading and a card with nothing on it teaches nothing.
        session.submit(new GameEvent.PaperCardCreated(theirs, theirs, PaperStock.BLANK,
                Component.translatable("tutorial.gathering.demonstration_card").getString()));

        TableBroadcast.sendToTable(level, tableOrigin);
        TableActions.openFor(learner, tableOrigin);
        LOGGER.info("Started a practice game at {} for {}",
                tableOrigin, learner.getGameProfile().getName());
        // Whose lesson this is, so logging out ends it rather than leaving the table with a
        // game nobody is playing and nobody can replace.
        LEARNING.put(learner.getUUID(), GlobalPos.of(level.dimension(), tableOrigin.immutable()));
        return Outcome.STARTED;
    }

    /**
     * Which table each learner is practicing at, so a disconnect can end it.
     * <p>A practice session has an owner in a way an ordinary game does not: it exists to
     * teach one person, and when that person goes there is nobody it is for. Without this the
     * table kept the session and the demonstration seat, the client forgot it was teaching
     * there, and on reconnect a new practice session was refused because the table already
     * had a game - a table left unusable by logging out, which an external review reproduced
     * through the shared disconnect hook.
     */
    private static final java.util.Map<UUID, GlobalPos> LEARNING = new java.util.HashMap<>();

    /**
     * Ends whatever this player was being taught, wherever it was.
     * <p>Idempotent, and called from {@link dev.gathering.server.PlayerGone#left} - the one
     * list both loaders run. Ordinary games are untouched: only a table this player started
     * practicing at is ended, and only while it is still marked practice.
     */
    public static void forget(net.minecraft.server.MinecraftServer server, UUID who) {
        if (server == null || who == null) {
            return;
        }
        GlobalPos at = LEARNING.remove(who);
        if (at == null) {
            return;
        }
        ServerLevel level = server.getLevel(at.dimension());
        if (level != null && isPracticeAt(level, at.pos())) {
            stop(level, at.pos());
        }
    }

    /** Drops everything, for a server that is stopping. */
    public static void clear() {
        LEARNING.clear();
    }

    /** Where somebody is being taught, for a test to ask. */
    public static boolean isLearning(UUID who) {
        return who != null && LEARNING.containsKey(who);
    }

    /**
     * Ends a practice game and leaves nothing behind.
     * <p>Safe to call when there is no practice game here: it does nothing, which is what
     * every caller wants, because they are a disconnect, a screen closing, a table being
     * broken and a player pressing Exit, and none of them can be sure they are first.
     */
    public static void stop(ServerLevel level, BlockPos tableOrigin) {
        // Whoever was being taught here is no longer being taught here, however this was
        // reached - the button, a disconnect, or the table being broken.
        GlobalPos here = GlobalPos.of(level.dimension(), tableOrigin.immutable());
        LEARNING.values().removeIf(here::equals);
        if (level == null || tableOrigin == null) {
            return;
        }
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .orElse(null);
        if (table == null || !table.isPractice()) {
            return;
        }
        // Through the ordinary ending, which settles and returns as it would for any game -
        // and returns nothing, because the deck was never taken into the table's keeping and
        // because giveBack refuses to hand a practice deck to anybody either way.
        TableSessions.end(level, tableOrigin, null, "practice over");
        release(level, tableOrigin, THE_DEMONSTRATION);
        TableBroadcast.sendToTable(level, tableOrigin);
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
        LEARNING.values().removeIf(GlobalPos.of(level.dimension(), tableOrigin.immutable())::equals);

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
     * Answers a client asking to start or stop practising.
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
                if (isPracticeAt(level, asked.table())
                        && TableSessions.seatIdOf(level, asked.table(), player.getUUID()).isPresent()) {
                    stop(level, asked.table());
                }
            }
        }
    }

    // ------------------------------------------------------------------ bits

    /**
     * Deals one seat a library of blank stock.
     * <p>Straight into the session rather than through a deck item, which is the whole of the
     * economy boundary: no {@code ItemStack} is ever made, so there is nothing to drop, trade,
     * put in a chest or hand back. The table is deliberately not asked to hold the deck.
     */
    private static void deal(GameSession session, SeatId seat) {
        List<CardIdentity> library = new ArrayList<>(PRACTICE_LIBRARY);
        for (int card = 0; card < PRACTICE_LIBRARY; card++) {
            library.add(PaperStock.BLANK.identity());
        }
        session.submit(new GameEvent.DeckLoaded(seat, library, List.of(), null));
        session.submit(new GameEvent.LibraryShuffled(seat, seat));
    }

    /** Whether anybody but this player is sitting at this cluster. */
    private static boolean somebodyElseIsHere(ServerLevel level, BlockPos tableOrigin, UUID learner) {
        for (SeatAnchor seat : TableClusters.at(level, tableOrigin).seats()) {
            Optional<UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, seat.cell()))
                    .flatMap(table -> table.occupantOf(seat.side()));
            if (occupant.isPresent() && !occupant.get().equals(learner)) {
                return true;
            }
        }
        return false;
    }

    /** Puts somebody in the first free seat, and says whether there was one. */
    private static boolean sit(
            ServerLevel level, BlockPos tableOrigin, List<SeatAnchor> seats, UUID who) {
        for (SeatAnchor seat : seats) {
            if (TableSeats.take(level, tableOrigin, seat.cell(), seat.side(), who)
                    == TableSeats.Claim.TAKEN) {
                return true;
            }
        }
        return false;
    }

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
