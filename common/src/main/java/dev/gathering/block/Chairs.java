package dev.gathering.block;

import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Sitting in a chair, and getting up out of one.
 * <p>A chair set against a table's edge is that edge's seat: sitting in it takes the seat exactly
 * as right-clicking the edge does - the same messages, the same game told, the same deck offered -
 * and opens the board if a game is on. Getting up, however it happens, gives the seat up exactly as
 * clicking your own edge does, and the cards stay on the table as they always do. A chair anywhere
 * else is only a chair.
 * <p>What is kept is the table's own record of who is sitting where; the thing a player rides is
 * only how Minecraft seats somebody. See {@link ChairSeat}.
 */
public final class Chairs {

    private Chairs() {
    }

    /** Sits this player in the chair at this position, taking the table's seat it faces if there is one. */
    public static void sit(ServerPlayer player, BlockPos chair, BlockState state) {
        if (player.isPassenger() || player.isShiftKeyDown()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getEntitiesOfClass(ChairSeat.class, new AABB(chair)).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.gathering.chair_taken"));
            return;
        }
        Direction facing = state.getValue(ChairBlock.FACING);
        Optional<FacingSeat> atATable = facingSeat(level, chair, facing);
        BlockPos tableOrigin = null;
        if (atATable.isPresent()) {
            FacingSeat seat = atATable.get();
            Optional<SeatAnchor> held = TableSeats.seatOf(level, seat.origin(), player.getUUID());
            boolean alreadyHere = held.filter(anchor -> anchor.cell().equals(seat.cell()) && anchor.side() == seat.side())
                    .isPresent();
            if (!alreadyHere
                    && TableBlock.sitAt(level, seat.origin(), seat.cell(), seat.side(), player) != TableSeats.Claim.TAKEN) {
                // Somebody else's seat, or a player already seated elsewhere at this table: said by sitAt,
                // and not sat in - a chair at a seat is that seat, not a place to wait for it.
                return;
            }
            tableOrigin = seat.origin();
        }
        ChairSeat seat = ChairSeat.in(level, chair, tableOrigin);
        level.addFreshEntity(seat);
        player.startRiding(seat, true);
        // Facing the table, so the board and the world agree about which way is forward.
        player.setYRot(facing.toYRot());
        player.setYHeadRot(facing.toYRot());
        if (tableOrigin != null && TableSessions.hasSession(level, tableOrigin)) {
            dev.gathering.server.TableActions.openFor(player, tableOrigin);
        }
    }

    /** A seat at a table, found from the chair against its edge. */
    record FacingSeat(BlockPos origin, TableCell cell, Side side) {
    }

    /**
     * The table seat a chair faces, if it faces one: the table block in front of it, the edge of that
     * table the chair is against, and whether the cluster counts that edge as a seat.
     */
    static Optional<FacingSeat> facingSeat(Level level, BlockPos chair, Direction facing) {
        BlockPos front = chair.relative(facing);
        BlockState there = level.getBlockState(front);
        if (!(there.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        BlockPos corner = TableBlock.originOf(there, front);
        BlockPos origin = TableSessions.anchorOf(level, corner).orElse(corner);
        TableCluster cluster = TableClusters.at(level, origin);
        TableCell cell = TableClusters.cellOf(origin, corner);
        Side side = TableClusters.sideFacing(facing.getOpposite());
        if (side == null || !TableSeats.isSeat(cluster, cell, side)) {
            return Optional.empty();
        }
        return Optional.of(new FacingSeat(origin, cell, side));
    }

    /**
     * A player got out of a chair: stood up, was knocked out of it, or the chair went. Gives up the seat
     * the chair took, if they still hold it - unless they left the server, which keeps a seat as it
     * always has.
     */
    static void gotUp(ServerPlayer player, ChairSeat seat) {
        seat.discard();
        if (player.hasDisconnected() || seat.tableOrigin() == null) {
            return;
        }
        BlockPos origin = seat.tableOrigin();
        if (TableSeats.seatOf(player.serverLevel(), origin, player.getUUID()).isPresent()) {
            TableBlock.standUp(player.serverLevel(), origin, player);
        }
    }

    /** The chair at this position is gone: whoever was in it gets up. */
    static void emptied(Level level, BlockPos chair) {
        for (ChairSeat seat : level.getEntitiesOfClass(ChairSeat.class, new AABB(chair))) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    /**
     * A player gave up a seat some other way - the board's own Leave table - so if they are sitting in
     * a chair at it, they get up out of it too.
     */
    public static void leftTheSeat(Level level, java.util.UUID player) {
        if (level.getPlayerByUUID(player) instanceof ServerPlayer sitting && sitting.getVehicle() instanceof ChairSeat) {
            sitting.stopRiding();
        }
    }
}
