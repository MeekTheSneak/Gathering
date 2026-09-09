package dev.gathering.core.ui;

/**
 * Where the camera over a table should be pointed, and how far it may be pushed.
 * <p>Three answers that have to come from one geometry, and did not. The eye is placed at the
 * middle of the whole cluster; the offset that focused a mat was measured from the middle of
 * one table; the pan clamp used one table's span on both axes; and the framing for "show
 * everything" used one table's span squared. Every one of those is right for a single table
 * and wrong the moment somebody pushes a second one against it, in a different direction each
 * time - the focused mat drifted sideways, the ends of a long row could not be panned to, and
 * the key that exists to fit the whole table on screen cut it off.
 * <p>Gathered here so the four cannot disagree again, and so the arithmetic can be checked
 * without a window: {@link TableTop} knows how big the cluster is and this says what the
 * camera should do about it.
 * <p>Pure.
 */
public final class TableFraming {

    private TableFraming() {
    }

    /**
     * How far the eye moves from the middle of the cluster to look at this spot, in blocks.
     *
     * @param top the whole cluster's surface
     * @param surfaceX where to look, in surface units across the shared surface
     */
    public static double focusAcross(TableTop top, double surfaceX) {
        return top.blocks(surfaceX - top.surfaceWidth() / 2.0);
    }

    /** The same, north to south. */
    public static double focusDown(TableTop top, double surfaceY) {
        return top.blocks(surfaceY - top.surfaceDepth() / 2.0);
    }

    /**
     * How far the eye may be pushed either side of the middle, in blocks.
     * <p>Half the cluster, per axis, so a row of four can be panned to either end of itself
     * and no further. Panning until the board is off screen is a way to lose the game you are
     * playing; being unable to reach one end of your own table is worse.
     */
    public static double panReachAcross(TableTop top) {
        return top.widthInBlocks() / 2;
    }

    /** The same, north to south. */
    public static double panReachDown(TableTop top) {
        return top.depthInBlocks() / 2;
    }

    /** How wide a thing "show everything" has to frame, in blocks. */
    public static double everythingAcross(TableTop top) {
        return top.widthInBlocks();
    }

    /** And how deep. Separately, because a cluster is a row and not a square. */
    public static double everythingDown(TableTop top) {
        return top.depthInBlocks();
    }
}
