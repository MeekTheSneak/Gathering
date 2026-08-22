package dev.gathering.block;

import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Taking and giving up a seat at a cluster of tables.
 *
 * <p>A seat belongs to an edge of one table, so the claim is stored on that table and comes
 * back with it. What this adds is the cluster's view: whether that edge is a seat at all -
 * an edge two tables share is not - and whether anyone anywhere in the cluster is seated,
 * which is what freezes the cluster's shape.
 */
public final class TableSeats {

    private TableSeats() {
    }

    /** Whether this edge of this table is one of the cluster's seats. */
    public static boolean isSeat(TableCluster cluster, TableCell cell, Side side) {
        return cluster.seats().contains(new SeatAnchor(cell, side));
    }

    /**
     * Takes a seat, and says what happened.
     *
     * <p>Every refusal is its own answer rather than a bare false, because "you cannot sit
     * there" and "somebody is already there" and "you are already sitting at this table" want
     * three different things said to the player.
     */
    public static Claim take(Level level, BlockPos clusterOrigin, TableCell cell, Side side, UUID player) {
        TableCluster cluster = TableClusters.at(level, clusterOrigin);
        if (!isSeat(cluster, cell, side)) {
            return Claim.NOT_A_SEAT;
        }
        Optional<TableBlockEntity> table = tableAt(level, clusterOrigin, cell);
        if (table.isEmpty()) {
            return Claim.NOT_A_SEAT;
        }
        if (seatOf(level, clusterOrigin, player).isPresent()) {
            return Claim.ALREADY_SEATED;
        }
        return table.get().claim(side, player) ? Claim.TAKEN : Claim.OCCUPIED;
    }

    /** Gives up whichever seat in this cluster the player holds. */
    public static boolean leave(Level level, BlockPos clusterOrigin, UUID player) {
        TableCluster cluster = TableClusters.at(level, clusterOrigin);
        for (TableCell cell : cluster.cells()) {
            Optional<TableBlockEntity> table = tableAt(level, clusterOrigin, cell);
            if (table.isEmpty()) {
                continue;
            }
            Optional<Side> held = table.get().sideHeldBy(player);
            if (held.isPresent()) {
                return table.get().release(held.get(), player);
            }
        }
        return false;
    }

    /** Which seat in this cluster the player holds, if any. */
    public static Optional<SeatAnchor> seatOf(BlockGetter level, BlockPos clusterOrigin, UUID player) {
        TableCluster cluster = TableClusters.at(level, clusterOrigin);
        for (TableCell cell : cluster.cells()) {
            Optional<Side> held = tableAt(level, clusterOrigin, cell).flatMap(t -> t.sideHeldBy(player));
            if (held.isPresent()) {
                return Optional.of(new SeatAnchor(cell, held.get()));
            }
        }
        return Optional.empty();
    }

    public static int occupiedSeats(BlockGetter level, BlockPos clusterOrigin) {
        TableCluster cluster = TableClusters.at(level, clusterOrigin);
        int taken = 0;
        for (SeatAnchor seat : cluster.seats()) {
            if (tableAt(level, clusterOrigin, seat.cell())
                    .flatMap(table -> table.occupantOf(seat.side()))
                    .isPresent()) {
                taken++;
            }
        }
        return taken;
    }

    /**
     * Whether the cluster's shape may change.
     *
     * <p>Adding or removing a table reshapes the perimeter, which moves the seats: somebody
     * registered at an edge could find that edge is now the middle of the surface. So while
     * anybody is seated the shape is frozen, which is also what the design says about a live
     * session.
     */
    public static boolean isShapeFrozen(BlockGetter level, BlockPos clusterOrigin) {
        return occupiedSeats(level, clusterOrigin) > 0;
    }

    /**
     * Whether this block may be broken.
     *
     * <p>Only tables answer no, and only while somebody at their cluster is seated. Refusing
     * has to happen at the loader's break event: a block cannot decline to be broken in
     * vanilla, and by the time the block itself hears about it the decision is made.
     */
    public static boolean mayBreak(BlockGetter level, BlockPos pos) {
        return TableBlock.entityAt(level, pos)
                .map(table -> !isShapeFrozen(level, table.getBlockPos()))
                .orElse(true);
    }

    private static Optional<TableBlockEntity> tableAt(
            BlockGetter level, BlockPos clusterOrigin, TableCell cell) {
        return TableBlock.entityAt(level, TableClusters.blockPos(clusterOrigin, cell));
    }

    /** What happened when somebody tried to sit down. */
    public enum Claim {
        TAKEN,
        OCCUPIED,
        ALREADY_SEATED,
        NOT_A_SEAT;

        public String messageKey() {
            return "message.gathering.seat_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
