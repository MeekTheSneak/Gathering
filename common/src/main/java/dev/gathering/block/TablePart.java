package dev.gathering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

/**
 * Which quarter of a table a block is.
 *
 * <p>A table is two blocks by two, and only one of the four is real: the north-west corner
 * carries the block entity and is what the rest of the mod means by "a table". The other
 * three know where it is and forward everything to it.
 *
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

    @Override
    public String getSerializedName() {
        return name;
    }
}
