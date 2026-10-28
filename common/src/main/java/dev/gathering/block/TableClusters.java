package dev.gathering.block;

import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The bridge between tables in a world and the cluster arithmetic in the pure core.
 * <p>Everything about which tables join up, how many seats that makes and where they go is
 * decided in {@code :core}, where it is checked against every shape a player can build. This
 * translates block positions into that vocabulary and back, and does nothing else - so a
 * table's behavior in the world and the rules that were tested are the same rules.
 */
public final class TableClusters {

    private TableClusters() {
    }

    /**
     * The cluster the table at this origin belongs to. Cells are relative to that origin.
     * <p>Tables set to be played apart are clusters of one: a long table split into 1v1 games is
     * several tables that happen to touch. Everything that asks which game a table is part of -
     * sessions, seats, boards, chat, the pot - asks here, so this is the whole of the split.
     */
    public static TableCluster at(BlockGetter level, BlockPos origin) {
        TableCell home = new TableCell(0, 0);
        // A table on its own seats across whichever edges its first sitter chose. Asked of this table,
        // because a cluster of one is this table; a line of more runs the way it runs.
        boolean turnedWhenAlone = level.getBlockEntity(origin) instanceof TableBlockEntity table && table.turned();
        if (playsApart(level, origin)) {
            return TableCluster.around(home, cell -> cell.equals(home) && isTableOrigin(level, blockPos(origin, cell)),
                    turnedWhenAlone);
        }
        return TableCluster.around(home, cell -> isTableOrigin(level, blockPos(origin, cell))
                && !playsApart(level, blockPos(origin, cell)), turnedWhenAlone);
    }

    /**
     * Every table physically joined to this one, played apart or not: the long table as it
     * stands in the world. What playing apart is switched for, all at once.
     */
    public static TableCluster touching(BlockGetter level, BlockPos origin) {
        return TableCluster.around(new TableCell(0, 0), cell -> isTableOrigin(level, blockPos(origin, cell)));
    }

    /** Whether the table with its corner here is set to be played on its own. */
    public static boolean playsApart(BlockGetter level, BlockPos origin) {
        return level.getBlockEntity(origin) instanceof TableBlockEntity table && table.playsApart();
    }

    /**
     * Whether a table placed with its corner here would be able to join what is already
     * there.
     * <p>Asked before the table exists, so it counts the neighboring clusters rather than
     * its own. Two separate clusters either side of the gap merge into one when it is filled,
     * which is why this cannot just look at the biggest of them.
     */
    public static boolean wouldFit(BlockGetter level, BlockPos origin) {
        // Small enough, and still a line. A table pushed against the front or back of another
        // takes an edge the game is played across away from both of them, and the people they
        // could still seat would be at the sides reading their own boards sideways. Refused
        // here, where a player is standing with the table in their hand and can be told, and
        // never as a seat that turns out to be unusable.
        return whyItWouldNotFit(level, origin).isEmpty();
    }

    /**
     * The shape this placement would leave behind: the new table and everything it touches.
     * <p>Two separate clusters either side of the gap merge into one when it is filled, which
     * is why this cannot just look at the biggest of them.
     */
    private static java.util.Set<TableCell> shapeAfterPlacing(BlockGetter level, BlockPos origin) {
        java.util.Set<TableCell> joined = new java.util.LinkedHashSet<>();
        joined.add(new TableCell(0, 0));
        for (Side side : Side.values()) {
            TableCell neighbor = new TableCell(0, 0).step(side);
            if (!isTableOrigin(level, blockPos(origin, neighbor))) {
                continue;
            }
            joined.addAll(TableCluster.around(
                    neighbor, cell -> isTableOrigin(level, blockPos(origin, cell))).cells());
        }
        return joined;
    }

    /**
     * Why a table would not go here, for the message a player gets when it does not.
     * <p>Two reasons and they are not the same: one is a cap somebody can work round by
     * building elsewhere, the other is a shape. A refusal that does not say which is a table
     * that just will not place.
     */
    public static String whyItWouldNotFit(BlockGetter level, BlockPos origin) {
        java.util.Set<TableCell> joined = shapeAfterPlacing(level, origin);
        if (joined.size() > TableCluster.MAX_TABLES) {
            return "message.gathering.cluster_full";
        }
        return TableCluster.seatsEverySide(joined) ? "" : "message.gathering.cluster_line";
    }

    /**
     * Where a seat is in the world: the block outside the middle of that edge of that table, which is
     * where its chair goes.
     */
    public static BlockPos seatPos(BlockPos origin, SeatAnchor seat) {
        BlockPos table = blockPos(origin, seat.cell());
        int across = TableCell.BLOCKS_PER_TABLE;
        int middle = across / 2;
        return switch (seat.side()) {
            // Measured from the table's corner, so its far edges are a whole table further out than
            // its near ones - the offsets are not symmetric, and assuming they are puts half the
            // seats inside the table.
            case NORTH -> table.offset(middle, 0, -1);
            case SOUTH -> table.offset(middle, 0, across);
            case WEST -> table.offset(-1, 0, middle);
            case EAST -> table.offset(across, 0, middle);
        };
    }

    /** Which edge of a table somebody standing here is at, if any. */
    public static Side sideFacing(Direction direction) {
        return switch (direction) {
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case EAST -> Side.EAST;
            case WEST -> Side.WEST;
            default -> null;
        };
    }

    /**
     * Which edge of a table somebody is at, from the face they clicked or else from where they
     * are standing.
     * <p>The face alone is not enough, and the case it misses is the ordinary one: a table is
     * waist height, so the thing in front of you when you walk up to one and right-click is
     * its top. That gave no side at all, and sitting down meant crouching to find a vertical
     * face - which nobody would ever guess at. Clicking the top means the edge you are
     * standing at, worked out from where you are against the middle of the table.
     */
    public static Side sideFrom(Direction face, Vec3 standing, BlockPos tableOrigin) {
        Side fromFace = sideFacing(face);
        if (fromFace != null) {
            return fromFace;
        }
        double middle = TableCell.BLOCKS_PER_TABLE / 2.0;
        double across = standing.x - (tableOrigin.getX() + middle);
        double down = standing.z - (tableOrigin.getZ() + middle);
        if (Math.abs(across) > Math.abs(down)) {
            return across < 0 ? Side.WEST : Side.EAST;
        }
        return down < 0 ? Side.NORTH : Side.SOUTH;
    }

    /** Which cell of a cluster a given table is, given where the cluster's own origin is. */
    public static TableCell cellOf(BlockPos clusterOrigin, BlockPos tableOrigin) {
        return new TableCell(
                Math.floorDiv(tableOrigin.getX() - clusterOrigin.getX(), TableCell.BLOCKS_PER_TABLE),
                Math.floorDiv(tableOrigin.getZ() - clusterOrigin.getZ(), TableCell.BLOCKS_PER_TABLE));
    }

    /** A cell of the cluster, back in world coordinates. A table is three blocks wide. */
    public static BlockPos blockPos(BlockPos origin, TableCell cell) {
        return origin.offset(cell.x() * TableCell.BLOCKS_PER_TABLE, 0, cell.z() * TableCell.BLOCKS_PER_TABLE);
    }

    private static boolean isTableOrigin(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof TableBlock
                && state.getValue(TableBlock.PART).isOrigin();
    }
}
