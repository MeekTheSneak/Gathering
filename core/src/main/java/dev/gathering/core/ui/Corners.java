package dev.gathering.core.ui;

/**
 * A card as it is drawn: its four corners, in order round it, each an x and a y.
 * <p>The shape both boards pick and write around. The flat board's card is a rectangle turned about its middle; the
 * block's is the same four corners run through the game's camera, which is a four-sided shape and no longer a
 * rectangle. Asked the same three questions either way - where the corners are, whether a point is on the card, and
 * the box round it - so the picker and what the screen writes over the board cannot disagree about where a card is.
 * <p>Pure.
 */
public final class Corners {

    /** Less area than this is none: a millionth of a millionth of a square pixel is rounding, not a card. */
    private static final double NO_AREA = 1e-12;

    private Corners() {
    }

    /**
     * The corners of a rectangle turned this far about its middle, in the space the rectangle is in, in order round
     * it: the turn every painter here gives a card, so they are the corners it is drawn with.
     */
    public static double[][] of(Rect where, int angle) {
        double turn = Math.toRadians(angle);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double halfWidth = where.width() / 2.0;
        double halfHeight = where.height() / 2.0;
        double[][] corners = new double[4][];
        int[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int corner = 0; corner < 4; corner++) {
            double dx = signs[corner][0] * halfWidth;
            double dy = signs[corner][1] * halfHeight;
            corners[corner] = new double[] {
                    where.centerX() + dx * cos - dy * sin,
                    where.centerY() + dx * sin + dy * cos};
        }
        return corners;
    }

    /**
     * Whether a point is on the shape these corners go round, in order, either way round. Its edges count as on it.
     * <p>A shape with no area holds nothing. A card squeezed to nothing - a pot slot rounded to no pixels at the
     * furthest zoom - has every corner on one point, and asked the usual way every point in the window is on
     * neither side of any of its edges, so it was under the cursor wherever the cursor went.
     */
    public static boolean inside(double[][] corners, double x, double y) {
        // Not "greater than nothing" alone: a shape whose corners are not numbers has an area that is not one
        // either, and holds nothing too.
        if (!(areaOf(corners) > NO_AREA)) {
            return false;
        }
        boolean anyLeft = false;
        boolean anyRight = false;
        for (int corner = 0; corner < corners.length; corner++) {
            double[] from = corners[corner];
            double[] to = corners[(corner + 1) % corners.length];
            double cross = (to[0] - from[0]) * (y - from[1]) - (to[1] - from[1]) * (x - from[0]);
            anyLeft |= cross > 0;
            anyRight |= cross < 0;
        }
        return !(anyLeft && anyRight);
    }

    /** The rectangle round the corners, out to the whole pixel each way. */
    public static Rect around(double[][] corners) {
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (double[] corner : corners) {
            left = Math.min(left, corner[0]);
            top = Math.min(top, corner[1]);
            right = Math.max(right, corner[0]);
            bottom = Math.max(bottom, corner[1]);
        }
        int x = (int) Math.floor(left);
        int y = (int) Math.floor(top);
        return new Rect(x, y, (int) Math.ceil(right) - x, (int) Math.ceil(bottom) - y);
    }

    /** How much the corners enclose, by the shoelace rule, whichever way round they go. */
    private static double areaOf(double[][] corners) {
        double twice = 0;
        for (int corner = 0; corner < corners.length; corner++) {
            double[] from = corners[corner];
            double[] to = corners[(corner + 1) % corners.length];
            twice += from[0] * to[1] - to[0] * from[1];
        }
        return Math.abs(twice) / 2;
    }
}
