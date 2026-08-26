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
 *
 * <p>Three gates, in order, and the order matters. Is there a table and a game there; is this
 * player in a seat at it; and does the move carry that player's own seat as its actor. Only
 * then does it reach {@code Authorization}, which is the same gate a move made server-side
 * goes through.
 *
 * <p>The actor check is the one that is easy to leave out and expensive to leave out.
 * Attribution is what makes "any seated player may move any public card" safe: the log says
 * who did it. A client that could sign a move with somebody else's name would take that away
 * and leave the permissiveness behind.
 */
public final class TableActions {

    /** How far a player may be from a table and still be playing at it. */
    private static final double REACH = 12.0d;

    private TableActions() {
    }

    public static void handle(ServerPlayer player, TableActionPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos clicked = payload.table();

        if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > REACH * REACH) {
            return;
        }
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return;
        }
        BlockPos origin = TableBlock.originOf(state, clicked);

        GameEvent event = accept(level, origin, player.getUUID(), payload.event()).orElse(null);
        if (event == null) {
            return;
        }
        GameSession session = TableSessions.sessionAt(level, origin).orElseThrow();

        GameSession.Result result = session.submit(event);
        if (result instanceof GameSession.Result.Rejected rejected) {
            player.sendSystemMessage(Component.literal(rejected.reason()));
            return;
        }

        // Giving up a seat is two stores, not one: the game's own seat state, which the fold
        // has just updated, and the block's record of who is sitting where, which is what
        // decides whose chair is free for the next player. Leaving either behind is a player
        // who has stood up in one of them and is still sitting down in the other.
        if (event instanceof GameEvent.SeatReleased) {
            TableSeats.leave(level, origin, player.getUUID());
            Antes.seatsChanged(level, origin);
        }

        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::setChanged);
        TableBroadcast.sendToTable(level, origin);

        // Last, and after the board has gone out: a move that ended the game is still a move,
        // and everybody should see the board it ended on before it is taken away.
        TableMatch.settleIfFinished(level, origin, session.state());
    }

    /**
     * The move, if this player is allowed to have made it.
     *
     * <p>Separate from the plumbing so the decision can be checked directly, because the
     * decision is the part with a security property in it and the plumbing is not.
     *
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

        // The one check that cannot be left out. Attribution is what makes "any seated player
        // may move any public card" safe: the log says who did it. A client that could sign a
        // move with somebody else's name would take that away and leave the permissiveness.
        return event.actor().equals(seat) ? Optional.of(event) : Optional.empty();
    }

    /**
     * Takes back a player's own most recent actions, if this table lets them.
     *
     * <p>Every judgement is the session's and is made here rather than trusted from the
     * packet: who is asking comes from the player it arrived from, and whether the rewind is
     * allowed - their own actions, this table's undo mode, and the hard rule that a rewind
     * never crosses an action that let somebody see something - is decided by the same code
     * that decides it for the interface. A client can ask for anything; it gets what the
     * table allows and a reason when it does not.
     */
    public static void handleUndo(ServerPlayer player, UndoPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos clicked = payload.table();
        if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5)
                > REACH * REACH) {
            return;
        }
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TableBlock)) {
            return;
        }
        BlockPos origin = TableBlock.originOf(state, clicked);
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
        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .ifPresent(TableBlockEntity::setChanged);
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
