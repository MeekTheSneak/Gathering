package dev.gathering.core.table;

/**
 * One table's place in a cluster, counted in tables rather than in blocks.
 *
 * <p>A table is two blocks by two, so its footprint is the unit that matters when working out
 * what touches what. Counting in blocks here would mean every piece of the arithmetic below
 * carrying a factor of two around, and getting one of them wrong makes tables merge that are
 * a block apart.
 */
public record TableCell(int x, int z) {

    /** How far apart two tables' origins are, in blocks, along one axis. */
    public static final int BLOCKS_PER_TABLE = 2;

    public TableCell step(Side side) {
        return new TableCell(x + side.stepX(), z + side.stepZ());
    }

}
