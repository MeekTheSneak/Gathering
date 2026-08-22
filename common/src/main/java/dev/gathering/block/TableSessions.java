package dev.gathering.block;

import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SessionSeed;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Starting, finding and ending the game on a cluster of tables.
 *
 * <p>A cluster runs one game, so one of its tables has to hold it. That is the cluster's
 * first cell in its own stable order - the same order that numbers the seats - so every table
 * in a cluster agrees on where the game is without anything having to be written down about
 * it.
 *
 * <p>Seats are numbered by that order too: seat <i>n</i> of the session is the <i>n</i>th of
 * the cluster's seat positions. The shape is frozen while anybody is seated, so those
 * numbers cannot move under a live game.
 */
public final class TableSessions {

    /** Commander, because that is the format the mod is built around. */
    public static final int DEFAULT_STARTING_LIFE = 40;

    private TableSessions() {
    }

    /** The table holding this cluster's game, whether or not there is one. */
    public static Optional<BlockPos> anchorOf(BlockGetter level, BlockPos tableOrigin) {
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        return cluster.isEmpty()
                ? Optional.empty()
                : Optional.of(TableClusters.blockPos(tableOrigin, cluster.cells().get(0)));
    }

    public static Optional<GameSession> sessionAt(BlockGetter level, BlockPos tableOrigin) {
        return anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .flatMap(TableBlockEntity::session);
    }

    public static boolean hasSession(BlockGetter level, BlockPos tableOrigin) {
        return anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .map(TableBlockEntity::hasSession)
                .orElse(false);
    }

    /**
     * Starts a game on this cluster.
     *
     * <p>Every seat the cluster has becomes a seat in the session, whether or not anybody is
     * in it: the shape is frozen for the duration, so the seats cannot move, and somebody
     * arriving later should find a seat waiting rather than a game that has no room.
     */
    public static Outcome start(Level level, BlockPos tableOrigin, int startingLife) {
        BlockPos anchor = anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null) {
            return Outcome.NO_TABLE;
        }
        if (table.hasSession()) {
            return Outcome.ALREADY_RUNNING;
        }

        TableCluster cluster = TableClusters.at(level, tableOrigin);
        List<SeatAnchor> anchors = cluster.seats();
        if (TableSeats.occupiedSeats(level, tableOrigin) == 0) {
            return Outcome.NOBODY_SEATED;
        }

        List<SeatId> seats = new ArrayList<>(anchors.size());
        for (int index = 0; index < anchors.size(); index++) {
            seats.add(new SeatId(index));
        }
        GameSession session = GameSession.create(
                seats, startingLife, SessionSeed.random(), UndoMode.shippedDefault());

        // Everybody already registered joins the game they were waiting for.
        for (int index = 0; index < anchors.size(); index++) {
            SeatAnchor seat = anchors.get(index);
            Optional<java.util.UUID> occupant = TableBlock
                    .entityAt(level, TableClusters.blockPos(tableOrigin, seat.cell()))
                    .flatMap(other -> other.occupantOf(seat.side()));
            if (occupant.isEmpty()) {
                continue;
            }
            Player player = level.getPlayerByUUID(occupant.get());
            String name = player == null ? "Player" : player.getGameProfile().getName();
            session.submit(new GameEvent.SeatTaken(new SeatId(index), new PlayerRef(occupant.get(), name)));
        }

        table.beginSession(session, startingLife);
        return Outcome.STARTED;
    }

    public static Outcome end(Level level, BlockPos tableOrigin, SeatId actor, String reason) {
        BlockPos anchor = anchorOf(level, tableOrigin).orElse(null);
        if (anchor == null) {
            return Outcome.NO_TABLE;
        }
        TableBlockEntity table = TableBlock.entityAt(level, anchor).orElse(null);
        if (table == null || !table.hasSession()) {
            return Outcome.NOT_RUNNING;
        }
        table.session().ifPresent(session -> session.submit(new GameEvent.SessionEnded(actor, reason)));
        table.endSession();
        // Told before it is forgotten, or everyone at the table keeps looking at the last
        // board they were sent - which is worse than an empty screen, because it looks live.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            dev.gathering.server.TableBroadcast.closeAtTable(server, tableOrigin);
        }
        return Outcome.ENDED;
    }

    /** Which session seat a player holds at this cluster, if any. */
    public static Optional<SeatId> seatIdOf(BlockGetter level, BlockPos tableOrigin, java.util.UUID player) {
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        return TableSeats.seatOf(level, tableOrigin, player)
                .map(cluster.seats()::indexOf)
                .filter(index -> index >= 0)
                .map(SeatId::new);
    }

    /** What came of asking. Each refusal is its own answer so it can be explained. */
    public enum Outcome {
        STARTED,
        ENDED,
        ALREADY_RUNNING,
        NOT_RUNNING,
        NOBODY_SEATED,
        NO_TABLE;

        public String messageKey() {
            return "message.gathering.session_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
