package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import dev.gathering.network.TableActionPayload;
import dev.gathering.network.UndoPayload;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A move a player made at a table.
 * <p>Three gates, in order, and the order matters. Is there a table and a game there; is this
 * player in a seat at it; and does the move carry that player's own seat as its actor. Only
 * then does it reach {@code Authorization}, which is the same gate a move made server-side
 * goes through.
 * <p>The actor check is the one that is easy to leave out and expensive to leave out.
 * Attribution is what makes "any seated player may move any public card" safe: the log says
 * who did it. A client that could sign a move with somebody else's name would take that away
 * and leave the permissiveness behind.
 */
public final class TableActions {

    private TableActions() {
    }

    /**
     * Moves each player may make: sixty a second sustained, a thousand at once. Far beyond
     * anybody playing - a pile carried card by card, or several full selections, fits in the
     * burst - and still a ceiling on a client that sends moves as fast as a socket allows, each
     * of which is folded, logged, saved and sent as a board to everybody at the table. The first
     * setting, thirty and 256, dropped moves from the scripted client's piles.
     */
    static final ActionBudget MOVES = new ActionBudget(60, 1000);

    /** Undos: each one refolds the whole game, so fewer. */
    static final ActionBudget UNDOS = new ActionBudget(2, 6);

    /** Forgets every player's budget, for a server that is stopping. */
    public static void clear() {
        MOVES.clear();
        UNDOS.clear();
    }

    public static void handle(ServerPlayer player, TableActionPayload payload) {
        if (!MOVES.spend(player.getUUID(), 1)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = TableReach.originFor(player, payload.table()).orElse(null);
        if (origin == null) {
            return;
        }
        if (apply(level, origin, player, payload.event())) {
            showTheBoard(level, origin);
        }
    }

    /**
     * Several moves in order, answered with one board.
     * <p>Each event is the single path's, gate for gate: {@link #apply} is the same method. What
     * differs is only when the table is shown the result. One board after the last move rather
     * than one after every move - except when a move ends the game, when the board it ended on
     * goes out first and the game is settled before anything else in the batch is looked at,
     * exactly as it is for single moves. Whatever follows that finds no game to act on, as a
     * separate packet arriving after it would have.
     * <p>The reach check is made once, for the batch. It is a check on where the player is
     * standing, and nothing in a batch moves the player.
     */
    public static void handleAll(ServerPlayer player, dev.gathering.network.TableActionsPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = TableReach.originFor(player, payload.table()).orElse(null);
        if (origin == null) {
            return;
        }
        int limit = Math.min(payload.events().size(), dev.gathering.core.ui.BulkLimit.MOST_AT_ONCE);
        if (!MOVES.spend(player.getUUID(), limit)) {
            return;
        }
        boolean unshown = false;
        for (int index = 0; index < limit; index++) {
            if (!apply(level, origin, player, payload.events().get(index))) {
                continue;
            }
            unshown = true;
            GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
            if (session != null && dev.gathering.core.match.GameOutcome.isFinished(session.state())) {
                showTheBoard(level, origin);
                unshown = false;
            }
        }
        if (unshown) {
            showTheBoard(level, origin);
        }
    }

    /**
     * Applies one move if it is allowed, and says whether it was.
     * <p>Everything a move does to the table except showing it: the gates, the rules, the
     * refusal, and the seat and deck bookkeeping when somebody stands up.
     */
    private static boolean apply(ServerLevel level, BlockPos origin, ServerPlayer player, byte[] bytes) {
        GameEvent event = accept(level, origin, player.getUUID(), bytes).orElse(null);
        if (event == null) {
            return false;
        }
        GameSession session = TableSessions.sessionAt(level, origin).orElseThrow();

        GameSession.Result result = session.submit(event);
        if (result instanceof GameSession.Result.Rejected rejected) {
            // Through Refusals rather than straight out. A verb applied to a selection arrives
            // here once per card, so a selection the table refuses used to be refused once per
            // card - forty identical lines, which is less informative than one.
            Refusals.tell(player, rejected.reason());
            return false;
        }

        // Giving up a seat is two stores, not one: the game's own seat state, which the fold
        // has just updated, and the block's record of who is sitting where, which is what
        // decides whose chair is free for the next player. Leaving either behind is a player
        // who has stood up in one of them and is still sitting down in the other.
        // A turn passing at a tournament table counts toward its extra turns once time is called.
        if (event instanceof GameEvent.TurnPassed) {
            dev.gathering.server.events.Events.turnPassed(level, origin);
        }
        if (event instanceof GameEvent.SeatReleased released) {
            TableSeats.leave(level, origin, player.getUUID());
            // And their deck comes with them. Leaving the table is the moment a player means
            // "I am done, give me my cards", and it used to give them nothing: a deck came
            // back only when the whole match ended, which is a thing the rest of the table is
            // in the middle of. The board they built stays on the felt either way - a seat
            // outlasts its player on purpose - so this hands back the deck item the table
            // took, not the game.
            TableSessions.returnDeckTo(level, origin, released.actor());
            Antes.seatsChanged(level, origin);
            PodSignups.seatReleased(level, origin, player.getUUID());
        }
        TableSessions.markDirty(level, origin);
        return true;
    }

    /** Sends the table its board, and then settles the game if that board is the last one. */
    private static void showTheBoard(ServerLevel level, BlockPos origin) {
        TableSessions.markDirty(level, origin);
        TableBroadcast.sendToTable(level, origin);
        // Last, and after the board has gone out: a move that ended the game is still a move,
        // and everybody should see the board it ended on before it is taken away.
        TableSessions.sessionAt(level, origin).ifPresent(session ->
                TableMatch.settleIfFinished(level, origin, session.state()));
    }

    /**
     * The move, if this player is allowed to have made it.
     * <p>Separate from the plumbing so the decision can be checked directly, because the
     * decision is the part with a security property in it and the plumbing is not.
     * <p>Refusals say nothing back. A client sending moves it cannot make is either broken or
     * probing, and an error message that distinguishes "no such table" from "not your seat"
     * answers a question that was not asked in good faith.
     */
    public static Optional<GameEvent> accept(
            net.minecraft.world.level.Level level, BlockPos origin, java.util.UUID player, byte[] bytes) {
        if (TableSessions.sessionAt(level, origin).isEmpty()) {
            return Optional.empty();
        }
        SeatId seat = TableSessions.seatIdOf(level, origin, player).orElse(null);
        if (seat == null) {
            return Optional.empty();
        }

        GameEvent event;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            event = EventCodec.read(in);
        } catch (IOException | RuntimeException e) {
            // Unreadable is refused rather than guessed at.
            return Optional.empty();
        }

        // Some events are the server's to write and nobody else's: the table's own lifecycle,
        // and the results of the dice. Authorization cannot refuse them, because it runs for
        // the server's own submits too; here is the one place that knows which side a packet
        // came from. A client that could send SessionEnded ended the game for the whole table
        // with nothing put away; one that could send DeckLoaded swapped its library mid-game;
        // one that could send DiceRolled rolled a twenty whenever it liked, and the log - the
        // only evidence a table has - said so.
        if (isTheServersToWrite(event)) {
            return Optional.empty();
        }

        // The one check that cannot be left out. Attribution is what makes "any seated player
        // may move any public card" safe: the log says who did it. A client that could sign a
        // move with somebody else's name would take that away and leave the permissiveness.
        return event.actor().equals(seat) ? Optional.of(event) : Optional.empty();
    }

    /**
     * The events no client is ever the author of.
     * <p>The list itself lives in {@link dev.gathering.core.game.ServerAuthored}, where the
     * test suite can walk the sealed event hierarchy and fail on an event nobody has
     * classified - so a verb added next month is refused here until somebody decides it is a
     * client's to send.
     */
    static boolean isTheServersToWrite(GameEvent event) {
        return dev.gathering.core.game.ServerAuthored.isTheServersToWrite(event);
    }

    /**
     * Takes back a player's own most recent actions, if this table lets them.
     * <p>Every judgment is the session's and is made here rather than trusted from the
     * packet: who is asking comes from the player it arrived from, and whether the rewind is
     * allowed - their own actions, this table's undo mode, and the hard rule that a rewind
     * never crosses an action that let somebody see something - is decided by the same code
     * that decides it for the interface. A client can ask for anything; it gets what the
     * table allows and a reason when it does not.
     */
    public static void handleUndo(ServerPlayer player, UndoPayload payload) {
        if (!UNDOS.spend(player.getUUID(), 1)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos clicked = payload.table();
        BlockPos origin = TableReach.originFor(player, clicked).orElse(null);
        if (origin == null) {
            return;
        }
        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        if (session == null || seat == null) {
            return;
        }
        int actions = Math.max(1, Math.min(UndoPayload.MOST_AT_ONCE, payload.actions()));

        // No consents, so anything needing them is refused with its reason. Collecting three
        // other players' agreement is a conversation this table cannot have yet, and asking
        // for it silently would be worse than saying so.
        GameSession.Result result = session.undo(seat, actions, java.util.List.of());
        if (result instanceof GameSession.Result.Rejected rejected) {
            player.sendSystemMessage(Component.literal(rejected.reason()));
            return;
        }
        TableSessions.markDirty(level, origin);
        TableBroadcast.sendToTable(level, origin);
    }

    /** Opens the board for a player who has just sat down at a running game. */
    public static Optional<SeatId> openFor(ServerPlayer player, BlockPos tableOrigin) {
        ServerLevel level = player.serverLevel();
        GameSession session = TableSessions.sessionAt(level, tableOrigin).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        Optional<SeatId> seat = TableSessions.seatIdOf(level, tableOrigin, player.getUUID());
        TableBroadcast.send(player, tableOrigin, session, seat, true);
        return seat;
    }
}
