package dev.gathering.core.ui;

/**
 * How far apart two things lying flat on each other on the table in the world are drawn.
 * <p>A playmat, its border, a zone's recess, the recess's edge, a card, the picture on its sleeve
 * and the number written over it are all flat, and all lie on one another. On one plane the depth
 * buffer picks a different winner every frame, which is the flicker. Lifted apart they stop
 * fighting - but only when the lift is larger than the smallest difference in depth the buffer can
 * tell apart, and that difference grows with the square of the distance to the camera and again as
 * the surface is seen more edge-on. A step of a ten-thousandth of a block held from a chair and
 * fought from across the room: the owner saw the mat buttons and the stacked cards flicker.
 * <p>So the step is chosen per frame from how far away the table is: a hair up close, where a
 * lift would show, and larger with distance, where the same lift is a fraction of a pixel.
 * <p>Pure, so the arithmetic is tested against the depth buffer it has to beat.
 */
public final class FlatLayers {

    /** The step up close, in blocks: invisible from a chair, and clear of the buffer there. */
    public static final double NEAREST_STEP = 0.00015;

    /** The largest step, in blocks. Eight of them are still under half a texel. */
    public static final double FARTHEST_STEP = 0.004;

    /** Within this many blocks the step is the nearest one. */
    public static final double CLOSE = 2.0;

    /** The near clipping plane Minecraft draws the world with, in blocks. */
    public static final double NEAR_PLANE = 0.05;

    /** The depth buffer's precision, in bits. */
    public static final int DEPTH_BITS = 24;

    /**
     * How much a card in a pile stands above the one under it, at the least, in steps.
     * <p>A card carries its sleeve's picture, then a backing and the writing over that, and all of
     * it has to stay under the next card up.
     */
    public static final int STEPS_PER_CARD = 5;

    private FlatLayers() {
    }

    /** One step between flat things, in blocks, for a table this far from the camera. */
    public static double step(double distance) {
        double far = Math.max(distance, CLOSE) / CLOSE;
        return Math.min(FARTHEST_STEP, NEAREST_STEP * far * far);
    }

    /**
     * The smallest difference in height the depth buffer tells apart on a flat surface this far
     * away, seen at this sine of the angle between the line of sight and the surface.
     * <p>A perspective depth buffer spends its precision near the camera: the depth one of its
     * values covers at distance {@code d} is about {@code d^2 / (near * 2^bits)}. A surface seen
     * edge-on turns a lift into less depth, by the sine of the angle it is seen at.
     */
    public static double resolvable(double distance, double sine) {
        double depth = distance * distance / (NEAR_PLANE * (1L << DEPTH_BITS));
        return depth / Math.max(sine, 1e-3);
    }

    /** How far a card in a pile stands over the one under it: its visible thickness, or more. */
    public static double perCard(double step, double thickness) {
        return Math.max(thickness, step * STEPS_PER_CARD);
    }
}
