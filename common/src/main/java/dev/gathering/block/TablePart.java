package dev.gathering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Which of a table's nine blocks a block is.
 * <p>A table is three blocks by three, and only one of the nine is real: the north-west corner
 * carries the block entity and is what the rest of the mod means by "a table". The other
 * eight know where it is and forward everything to it.
 * <p>Doing it this way rather than with a separate controller block means a table cannot be
 * broken into a state where most of it exists without the corner that owns the game.
 * <p>Named for where each block is from the middle of the table: the four corners carry the
 * legs, the four edges and the middle are felt over the apron.
 */
public enum TablePart implements StringRepresentable {

    NORTH_WEST("north_west", 0, 0),
    NORTH("north", 1, 0),
    NORTH_EAST("north_east", 2, 0),
    WEST("west", 0, 1),
    MIDDLE("middle", 1, 1),
    EAST("east", 2, 1),
    SOUTH_WEST("south_west", 0, 2),
    SOUTH("south", 1, 2),
    SOUTH_EAST("south_east", 2, 2);

    private final String name;
    private final int offsetX;
    private final int offsetZ;

    TablePart(String name, int offsetX, int offsetZ) {
        this.name = name;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    /** The corner that owns the table. */
    public static TablePart origin() {
        return NORTH_WEST;
    }

    public boolean isOrigin() {
        return this == NORTH_WEST;
    }

    /** Whether this is one of the four corners, which carry the legs. */
    public boolean isCorner() {
        return offsetX != 1 && offsetZ != 1;
    }

    /** Where this block sits, given where the table's own corner is. */
    public BlockPos offsetFrom(BlockPos origin) {
        return origin.offset(offsetX, 0, offsetZ);
    }

    /** Where the table's corner is, given where this block is. */
    public BlockPos originFrom(BlockPos here) {
        return here.offset(-offsetX, 0, -offsetZ);
    }

    /**
     * Which block this becomes when the whole table is turned.
     * <p>A structure is placed at one of four rotations, block by block, and a table whose
     * blocks were not turned with it would come out of the ground as nine north-west corners
     * - nine tables in the space of one, none of them whole. So each block is turned the way
     * the building is.
     * <p>Worked out from the direction each block lies from the middle rather than from a
     * table of answers: turning north-west a quarter clockwise is north-east, and that is the
     * whole rule.
     */
    public TablePart rotated(Rotation rotation) {
        int x = fromTheMiddle(offsetX);
        int z = fromTheMiddle(offsetZ);
        return switch (rotation == null ? Rotation.NONE : rotation) {
            case CLOCKWISE_90 -> pointingAt(-z, x);
            case CLOCKWISE_180 -> pointingAt(-x, -z);
            case COUNTERCLOCKWISE_90 -> pointingAt(z, -x);
            default -> this;
        };
    }

    /** The same, for a table reflected rather than turned. */
    public TablePart mirrored(Mirror mirror) {
        int x = fromTheMiddle(offsetX);
        int z = fromTheMiddle(offsetZ);
        return switch (mirror == null ? Mirror.NONE : mirror) {
            case LEFT_RIGHT -> pointingAt(x, -z);
            case FRONT_BACK -> pointingAt(-x, z);
            default -> this;
        };
    }

    /** An offset of nought, one or two, as a step away from the middle of the table. */
    private static int fromTheMiddle(int offset) {
        return offset - 1;
    }

    private static TablePart pointingAt(int x, int z) {
        for (TablePart part : values()) {
            if (fromTheMiddle(part.offsetX) == x && fromTheMiddle(part.offsetZ) == z) {
                return part;
            }
        }
        return NORTH_WEST;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
