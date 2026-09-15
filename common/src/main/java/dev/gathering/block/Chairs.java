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
 * <p>A chair set against the middle of a table's edge is that edge's seat, and the only way to take
 * one: sitting in it takes the seat - the same messages, the same game told, the same deck offered -
 * and opens the board if a game is on, or the choice of game if there is none yet. Getting up, however it happens, gives the seat up exactly as
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
        Optional<FacingSeat> against = atATable.isPresent() ? atATable : againstATable(level, chair, facing);
        if (atATable.isEmpty() && against.isPresent()) {
            // Against a table but not at a seat: off the middle of an edge, or at an edge nobody may sit at -
            // the end of a line, or the side of a table already being played across the other way. Said
            // rather than sat in as a chair that is only a chair: somebody in a chair at a table looks, to
            // everybody including themselves, like somebody playing at it.
            boolean seatHere = TableSeats.couldSeat(level, against.get().origin(), against.get().cell(), against.get().side());
            player.displayClientMessage(Component.translatable(seatHere
                    ? "message.gathering.chair_off_center" : "message.gathering.seat_not_a_seat"), true);
            return;
        }
        // Into the chair first, and only then the table's seat, undone if the table says no. The seat was
        // claimed first once, and the mount's own answer ignored: a mount something refused - another mod,
        // a cancelled spawn - left a player standing beside the table holding its seat.
        ChairSeat seat = ChairSeat.in(level, chair, null);
        if (!level.addFreshEntity(seat)) {
            return;
        }
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return;
        }
        // Facing the table, so the board and the world agree about which way is forward.
        player.setYRot(facing.toYRot());
        player.setYHeadRot(facing.toYRot());
        if (atATable.isEmpty()) {
            return;
        }
        FacingSeat at = atATable.get();
        Optional<SeatAnchor> held = TableSeats.seatOf(level, at.origin(), player.getUUID());
        boolean alreadyHere = held.filter(anchor -> anchor.cell().equals(at.cell()) && anchor.side() == at.side())
                .isPresent();
        if (!alreadyHere
                && TableBlock.sitAt(level, at.origin(), at.cell(), at.side(), player) != TableSeats.Claim.TAKEN) {
            // Somebody else's seat, somebody's cards on it, or a player already seated elsewhere at this
            // table: said by sitAt, and back out of the chair - a chair at a seat is that seat, not a place
            // to wait for it. Out before the seat is marked as this chair's, so getting up gives nothing up.
            player.stopRiding();
            seat.discard();
            return;
        }
        seat.holdsTheSeatAt(at.origin());
        TableBlock.satDown(player, at.origin());
    }

    /** A seat at a table, found from the chair against its edge. */
    record FacingSeat(BlockPos origin, TableCell cell, Side side) {
    }

    /**
     * The table seat a chair faces, if it faces one: the table block in front of it, the edge of that
     * table the chair is against, and whether the cluster counts that edge as a seat.
     */
    static Optional<FacingSeat> facingSeat(Level level, BlockPos chair, Direction facing) {
        return againstATable(level, chair, facing)
                .filter(seat -> chair.equals(TableClusters.seatPos(seat.origin(), new SeatAnchor(seat.cell(), seat.side()))))
                .filter(seat -> TableSeats.couldSeat(level, seat.origin(), seat.cell(), seat.side()));
    }

    /** The table edge a chair is set against and facing, wherever along the edge it is. */
    private static Optional<FacingSeat> againstATable(Level level, BlockPos chair, Direction facing) {
        BlockPos front = chair.relative(facing);
        BlockState there = level.getBlockState(front);
        if (!(there.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        BlockPos corner = TableBlock.originOf(there, front);
        BlockPos origin = TableSessions.anchorOf(level, corner).orElse(corner);
        TableCell cell = TableClusters.cellOf(origin, corner);
        Side side = TableClusters.sideFacing(facing.getOpposite());
        return side == null ? Optional.empty() : Optional.of(new FacingSeat(origin, cell, side));
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
