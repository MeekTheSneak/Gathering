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
 * A game somebody can learn the controls in, that cannot give them anything.
 * <p>The guided first game needs a real board: real cards to draw, a real hand, real keys, the
 * real server deciding what happened. Anything less teaches the controls of a mock-up. So this
 * is an ordinary session at an ordinary table - the same events, the same authorization, the
 * same view filtering - with exactly one thing different about it, which the table records and
 * everything that hands cards to a person checks.
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
     * Starts a practice game for one player at this table.
     * <p>Refuses rather than clearing anything out of the way. A table with a game on it, or
     * with anybody else sitting at it, is somebody's evening - a tutorial that took it over
     * would be a worse first impression than no tutorial at all.
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
                Outcome how = start(player, asked.table());
                player.sendSystemMessage(Component.translatable(how.messageKey()));
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
