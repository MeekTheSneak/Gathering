package dev.gathering.core.ui;

import java.util.List;

/**
 * How high the board on the block lifts the pot: onto whatever stands where it lies, beside the table.
 * <p>The pot is laid out past the east edge of the whole table, on the felt's own plane, on both boards - so that
 * seen from above the two agree about where it is. In the world that place is whatever the player built there, and a
 * wall, a chest or raised ground against that side of the table covered the pot on the block while the screen still
 * hovered and read the cards nobody could see. The owner left where it goes to judgment (working record, decisions,
 * item 12, 2026-10-27), and the judgment is this: on the block only, the same place seen from above, lifted onto the
 * top of what stands there - the same standoff over it that the board has over its own felt.
 * <p>What stands there is told by its collision shapes, which are what a player stands on and what a card could be put
 * down on; a flower or a torch has none, and is drawn over. Only the boxes under the pot's tray count: a fence post at
 * the far side of a block the tray only reaches the edge of holds nothing up. And only what rises from the felt's own
 * height, block on block: the pot goes onto anything it would otherwise be inside, then onto anything standing on
 * that, and so on. An overhang with air under it - a shelf, leaves, the ceiling of a low room - is not stood on:
 * counted, a ceiling two blocks over the table's own block lifted the pot into the ceiling. The pot is one flat tray,
 * so anything under any part of it that it would be inside lifts the whole of it - leaves over the next block, at the
 * height a wall beside them lifted the pot to, lift it onto the leaves, or part of it would be inside them.
 * <p>Never past {@link #MOST}: the table camera looks down from about two blocks over the felt, and a pot near the eye
 * would be drawn larger than the table; a wall taller than that, or an overhang, still hides it.
 * <p>Plain numbers, so it is checked here rather than by looking at the world.
 */
public final class PotLift {

    /** Where the felt is in its own block layer, in blocks: the table's top is fifteen sixteenths up. */
    public static final double FELT = 15.0 / 16.0;

    /**
     * The most the pot is lifted above the felt, in blocks: room for a wall two blocks high, or a chest on a block,
     * beside a table standing on the floor - two blocks, less the felt's own height - and a little more.
     */
    public static final double MOST = 1.125;

    /** The pot's tray seen from above, in the world's blocks: west to east along x, north to south along z. */
    public record Footprint(double west, double north, double east, double south) {
    }

    /**
     * One box of a block's collision shape: where it lies seen from above, in the world's blocks, and the heights it
     * spans, in blocks from the bottom of the felt's own block layer - the block in that layer from 0 to 1, the one
     * above it from 1 to 2, and a fence's post past its block's top.
     */
    public record Box(double west, double north, double east, double south, double bottom, double top) {
    }

    private PotLift() {
    }

    /** How many block layers, from the felt's own, can hold something the pot is lifted onto. */
    public static int layersToAsk() {
        return (int) Math.ceil(FELT + MOST);
    }

    /**
     * How far above the felt the pot goes, in blocks: onto the top of whatever stands under its tray from the felt's
     * own height up, block on block, nought where nothing does, and never more than {@link #MOST}.
     *
     * @param tray     where the pot's tray lies, seen from above
     * @param standing collision boxes in and around the tray's footprint; a box not under the tray, or with no
     *     height, holds nothing up
     */
    public static double above(Footprint tray, List<Box> standing) {
        double height = FELT;
        boolean raised = true;
        while (raised && height < FELT + MOST) {
            raised = false;
            for (Box box : standing) {
                if (box == null || !(box.top() > box.bottom()) || !under(tray, box)) {
                    continue;
                }
                // Reaching from at or under the pot to over it: the pot is inside it, or it stands on what the pot
                // was just put on. Starting above the pot, it is an overhang, which is not stood on.
                if (box.bottom() <= height + TOUCHING && box.top() > height + TOUCHING) {
                    height = box.top();
                    raised = true;
                }
            }
        }
        return Math.min(MOST, height - FELT);
    }

    /** Whether some of this box lies under the tray, seen from above - more than an edge they share. */
    private static boolean under(Footprint tray, Box box) {
        return box.east() > tray.west() + TOUCHING && box.west() < tray.east() - TOUCHING
                && box.south() > tray.north() + TOUCHING && box.north() < tray.south() - TOUCHING;
    }

    /** How close two lengths may be and still meet, against a shape's bounds read back in floating point. */
    private static final double TOUCHING = 1e-6;
}
