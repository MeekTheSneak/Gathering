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

    /**
     * Everything "show everything" frames on the block, in surface units: the whole cluster, every hand held past
     * its edges, and whatever lies beside it, such as the pot.
     * <p>The hands both ways from the middle rather than one strip at each edge, as far as the furthest one
     * reaches: the camera looks at the middle of the table, so a hand past one edge needs the same room kept at the
     * other. Framed on the table alone, the far player's hand went under the strip along the top.
     * <p>The camera is pointed at the middle of this rather than of the table, which with nothing beside the table
     * is the same place. The flat board frames the surface, the hands and the pot together the same way: see
     * {@link BoardGeometry#showEverything(Rect)}. Framed without the pot, the block left it off the edge of the
     * window on the key a player reaches for to find what is at stake.
     *
     * @param surface the board laid out on the cluster, which says where its hands are held
     * @param beside  a rectangle in surface units lying off the table, or {@link Rect#NONE}
     */
    public static Rect everything(TableSurface surface, Rect beside) {
        int past = handsPast(surface);
        Rect table = new Rect(0, -past, Math.max(1, surface.width()), Math.max(1, surface.height()) + 2 * past);
        if (beside.isEmpty()) {
            return table;
        }
        int left = Math.min(table.x(), beside.x());
        int top = Math.min(table.y(), beside.y());
        int right = Math.max(table.right(), beside.right());
        int bottom = Math.max(table.bottom(), beside.bottom());
        return new Rect(left, top, right - left, bottom - top);
    }

    /** How far past the table's edge, north or south, the furthest hand reaches, in surface units. */
    private static int handsPast(TableSurface surface) {
        Rect reach = surface.handReach();
        if (reach.isEmpty()) {
            return 0;
        }
        return Math.max(Math.max(0, -reach.y()), Math.max(0, reach.bottom() - surface.height()));
    }
}
