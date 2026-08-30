package dev.gathering.core.ui;

/**
 * The surface of a card, as quads, with its corners rounded off.
 *
 * <p>A card is not a rectangle. It is a rectangle with its corners cut, and until this existed
 * the mod drew it as one - which reads as a picture of a card rather than a card, and reads
 * that way most strongly at exactly the moment somebody is holding one up to look at it.
 *
 * <p>The picture the mod fetches is a rectangle: the scan's corners are the black border,
 * squared off, because a photograph of a card has to be. So the rounding is the mod's to do,
 * and it is done by covering the card with quads that stop where the card stops rather than by
 * drawing the whole rectangle and painting the corners out. Painting them out would need to
 * know what is behind the card, which changes from screen to screen.
 *
 * <p>Walked as three grids and four fans: a tall panel down the middle, a strip either side
 * between the corners, and an arc at each corner. Every one of them is emitted in the same
 * winding, so a caller can hand the lot to one buffer.
 *
 * <p><b>Why this is the containment guarantee.</b> Everything drawn on a card in this mod -
 * the printed face, and the shine on a foil - is drawn from these points and no others. So the
 * question "can the shine leave the card" is not a question about clipping or angles or
 * scissors; it is the question of whether any point this emits is outside the card's outline,
 * which is a fact about arithmetic and is checked as one.
 *
 * <p>Pure: no Minecraft, so the shape is tested rather than looked at.
 */
public final class CardMesh {

    /**
     * How big the corner cut is, as a share of the card's width.
     *
     * <p>A Magic card is 63 by 88 millimeters with a corner radius of about three, which is
     * this. Taken off the width and converted for the height by the caller's aspect, so the
     * cut is a circle rather than an ellipse - a rounded corner that is wider than it is tall
     * is the thing that makes a shape look like a button instead of a card.
     */
    public static final float CORNER = 0.048f;

    /** The most of the card a corner may eat, for a box nothing like a card's shape. */
    private static final float MOST = 0.49f;

    /** One quad of the surface, in card coordinates: zero to one across and down. */
    @FunctionalInterface
    public interface Quads {
        void quad(
                float u1, float v1, float u2, float v2,
                float u3, float v3, float u4, float v4);
    }

    private CardMesh() {
    }

    /** How far in from the side a corner reaches, for a card of this shape. */
    public static float cornerAcross() {
        return CORNER;
    }

    /** And how far down, so that the two together are a circle rather than an oval. */
    public static float cornerDown(float aspect) {
        return Math.min(MOST, CORNER * Math.max(0f, aspect));
    }

    /**
     * Covers the card in quads, corners included.
     *
     * @param aspect the card's width divided by its height, which is what makes the corner
     *     cut circular rather than oval
     * @param columns how finely the middle is divided across, and the sides in proportion
     * @param rows how finely it is divided down
     * @param arcSteps how many pieces each corner's curve is drawn in
     */
    public static void walk(float aspect, int columns, int rows, int arcSteps, Quads out) {
        float across = Math.min(MOST, CORNER);
        float down = cornerDown(aspect);
        int middleColumns = Math.max(1, columns);
        int sideColumns = Math.max(2, Math.round(columns * across / Math.max(0.01f, 1f - 2f * across)));
        int deep = Math.max(1, rows);
        int arc = Math.max(2, arcSteps);

        grid(across, 0f, 1f - across, 1f, middleColumns, deep, out);
        grid(0f, down, across, 1f - down, sideColumns, deep, out);
        grid(1f - across, down, 1f, 1f - down, sideColumns, deep, out);

        corner(across, down, across, down, Math.PI, Math.PI * 1.5, arc, out);
        corner(1f - across, down, across, down, Math.PI * 1.5, Math.PI * 2.0, arc, out);
        corner(1f - across, 1f - down, across, down, 0.0, Math.PI * 0.5, arc, out);
        corner(across, 1f - down, across, down, Math.PI * 0.5, Math.PI, arc, out);
    }

    /** One rectangular patch of the surface, divided up. */
    private static void grid(
            float left, float top, float right, float bottom, int columns, int rows, Quads out) {
        if (right <= left || bottom <= top) {
            return;
        }
        float wide = (right - left) / columns;
        float tall = (bottom - top) / rows;
        for (int column = 0; column < columns; column++) {
            float u1 = left + column * wide;
            float u2 = column + 1 == columns ? right : u1 + wide;
            for (int row = 0; row < rows; row++) {
                float v1 = top + row * tall;
                float v2 = row + 1 == rows ? bottom : v1 + tall;
                out.quad(u1, v1, u1, v2, u2, v2, u2, v1);
            }
        }
    }

    /**
     * One rounded corner, as a fan of slivers from the middle of its curve.
     *
     * <p>Emitted as quads with the last point repeated, because a repeated point is a triangle
     * with a zero-area triangle behind it and the buffer this goes into takes quads. Wound the
     * same way round as the grids above - checked rather than assumed, because a corner wound
     * the other way is a corner the interface culls and nobody sees the hole until they look
     * for it.
     */
    private static void corner(
            float centerU, float centerV, float across, float down,
            double from, double to, int steps, Quads out) {
        float previousU = (float) (centerU + across * Math.cos(from));
        float previousV = (float) (centerV + down * Math.sin(from));
        for (int step = 1; step <= steps; step++) {
            double angle = from + (to - from) * step / steps;
            float u = (float) (centerU + across * Math.cos(angle));
            float v = (float) (centerV + down * Math.sin(angle));
            if (turnsTheWrongWay(centerU, centerV, previousU, previousV, u, v)) {
                out.quad(centerU, centerV, u, v, previousU, previousV, previousU, previousV);
            } else {
                out.quad(centerU, centerV, previousU, previousV, u, v, u, v);
            }
            previousU = u;
            previousV = v;
        }
    }

    /**
     * Whether this sliver would be wound the opposite way from the grids.
     *
     * <p>A grid quad runs top left, bottom left, bottom right, which in card coordinates has a
     * negative cross product. A sliver has to match, or it faces away and is not drawn.
     */
    private static boolean turnsTheWrongWay(
            float centerU, float centerV, float firstU, float firstV, float secondU, float secondV) {
        float cross = (firstU - centerU) * (secondV - centerV) - (firstV - centerV) * (secondU - centerU);
        return cross > 0f;
    }

    /**
     * Whether a point is on the card at all, corners included.
     *
     * <p>What "inside the card" means, written once, so the drawing and the check that the
     * drawing stays inside cannot be measuring two different shapes.
     */
    public static boolean holds(float u, float v, float aspect, float slack) {
        float across = Math.min(MOST, CORNER);
        float down = cornerDown(aspect);
        if (u < -slack || u > 1f + slack || v < -slack || v > 1f + slack) {
            return false;
        }
        float overU = u < across ? across - u : (u > 1f - across ? u - (1f - across) : 0f);
        float overV = v < down ? down - v : (v > 1f - down ? v - (1f - down) : 0f);
        if (overU <= 0f || overV <= 0f) {
            return true;
        }
        float unitU = overU / across;
        float unitV = overV / down;
        return unitU * unitU + unitV * unitV <= (1f + slack) * (1f + slack);
    }

    /** How much of the unit square a card of this shape actually covers. */
    public static double area(float aspect) {
        float across = Math.min(MOST, CORNER);
        float down = cornerDown(aspect);
        return 1.0 - (4.0 - Math.PI) * across * down;
    }
}
