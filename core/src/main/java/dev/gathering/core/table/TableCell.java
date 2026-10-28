package dev.gathering.core.table;

/**
 * One table's place in a cluster, counted in tables rather than in blocks.
 * <p>A table is three blocks by three, so its footprint is the unit that matters when working out
 * what touches what. Counting in blocks here would mean every piece of the arithmetic below
 * carrying a factor of three around, and getting one of them wrong makes tables merge that are
 * a block apart.
 * <p>Three rather than two since the owner's playtest (2026-09-15): a two-block table has no middle
 * block on an edge, so a chair could not be set at the middle of one, and there was no room on a
 * mat for zones the size of the cards.
 */
public record TableCell(int x, int z) {

    /** How far apart two tables' origins are, in blocks, along one axis. */
    public static final int BLOCKS_PER_TABLE = 3;

    public TableCell step(Side side) {
        return new TableCell(x + side.stepX(), z + side.stepZ());
    }

}
