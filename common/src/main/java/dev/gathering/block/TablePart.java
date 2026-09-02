package dev.gathering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Which quarter of a table a block is.
 * <p>A table is two blocks by two, and only one of the four is real: the north-west corner
 * carries the block entity and is what the rest of the mod means by "a table". The other
 * three know where it is and forward everything to it.
 * <p>Doing it this way rather than with a separate controller block means a table cannot be
 * broken into a state where three quarters of it exist without the quarter that owns the
 * game.
 */
public enum TablePart implements StringRepresentable {

    NORTH_WEST("north_west", 0, 0),
    NORTH_EAST("north_east", 1, 0),
    SOUTH_WEST("south_west", 0, 1),
    SOUTH_EAST("south_east", 1, 1);

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

    /** Where this quarter sits, given where the table's own corner is. */
    public BlockPos offsetFrom(BlockPos origin) {
        return origin.offset(offsetX, 0, offsetZ);
    }

    /** Where the table's corner is, given where this quarter is. */
    public BlockPos originFrom(BlockPos here) {
        return here.offset(-offsetX, 0, -offsetZ);
    }

    /**
     * Which quarter this becomes when the whole table is turned.
     * <p>A structure is placed at one of four rotations, block by block, and a table whose
     * quarters were not turned with it would come out of the ground as four north-west
     * corners overlapping - four tables in the space of one, none of them whole. So the
     * quarter is turned the way the building is.
     * <p>Worked out from the corner each quarter points at rather than from a table of
     * sixteen answers: turning north-west a quarter clockwise is north-east, and that is the
     * whole rule.
     */
    public TablePart rotated(Rotation rotation) {
        int x = corner(offsetX);
        int z = corner(offsetZ);
        return switch (rotation == null ? Rotation.NONE : rotation) {
            case CLOCKWISE_90 -> pointingAt(-z, x);
            case CLOCKWISE_180 -> pointingAt(-x, -z);
            case COUNTERCLOCKWISE_90 -> pointingAt(z, -x);
            default -> this;
        };
    }

    /** The same, for a table reflected rather than turned. */
    public TablePart mirrored(Mirror mirror) {
        int x = corner(offsetX);
        int z = corner(offsetZ);
        return switch (mirror == null ? Mirror.NONE : mirror) {
            case LEFT_RIGHT -> pointingAt(x, -z);
            case FRONT_BACK -> pointingAt(-x, z);
            default -> this;
        };
    }

    /** An offset of nought or one, as a direction away from the middle of the table. */
    private static int corner(int offset) {
        return offset == 0 ? -1 : 1;
    }

    private static TablePart pointingAt(int x, int z) {
        for (TablePart part : values()) {
            if (corner(part.offsetX) == x && corner(part.offsetZ) == z) {
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
