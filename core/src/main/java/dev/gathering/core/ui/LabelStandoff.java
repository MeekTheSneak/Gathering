package dev.gathering.core.ui;

/**
 * How far a label floating over a block is kept from the eye reading it.
 * <p>A label over a table or a Scorekeeper's Desk is a flat sheet of writing turned to face
 * the camera, drawn at a fixed scale in world units. That scale is what makes it readable
 * across a hall, and it is also what breaks it in a doorway: walk up to the desk and the
 * sheet is a hand's width from your face, so a line two and a half blocks wide covers four
 * screens and the player reads the middle three letters of it. The parts that are not on the
 * screen any more are the parts that "phase out of existence".
 * <p>So the sheet is never allowed closer than {@link #CLOSEST}. Inside that, it slides back
 * along the line from the eye to where it belongs, which keeps it the size it is at arm's
 * length however close the player stands - and keeps it in front of the near clip plane,
 * which is the other way a sheet through somebody's head disappears.
 * <p>It moves rather than shrinks on purpose. Shrinking would keep the anchor and change the
 * scale, and the scale is shared with every other label in the room; one label drawn smaller
 * than the rest reads as a different kind of label rather than as a nearer one.
 */
public final class LabelStandoff {

    /**
     * How many blocks one pixel of label text covers.
     * <p>The scale the labels are drawn at, kept here rather than only in the renderer so the
     * standoff and the culling box below are worked out in the same units the text is in.
     */
    public static final double PER_PIXEL = 0.025;

    /** The step from one line of a label to the next, in font pixels. */
    public static final int LINE_PIXELS = 10;

    /** How wide a label is allowed to get before anything is done about it, in font pixels. */
    public static final int WIDEST_PIXELS = 320;

    /** The most lines any of these labels carries: a name, where it has got to, and a clock. */
    public static final int MOST_LINES = 4;

    /**
     * The nearest the writing ever comes, in blocks.
     * <p>Far enough that the longest line fits across an ordinary window, near enough that it
     * is still the label over the block in front of you rather than a sign in the distance.
     */
    public static final double CLOSEST = 2.5;

    /** A point in the world: where the writing ends up, or where the eye is. */
    public record Spot(double x, double y, double z) {
    }

    private LabelStandoff() {
    }

    /**
     * Where the writing is drawn, given where it belongs and where it is being read from.
     * <p>The anchor itself whenever the eye is far enough away, which is almost always.
     */
    public static Spot keptBack(Spot anchor, Spot eye) {
        double dx = anchor.x() - eye.x();
        double dy = anchor.y() - eye.y();
        double dz = anchor.z() - eye.z();
        double away = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (away >= CLOSEST) {
            return anchor;
        }
        if (away <= 0) {
            // The eye is exactly where the writing is, which leaves no direction to back off
            // along. Straight up is the one answer that is never into the block the label
            // belongs to, and standing on the block is how a player gets here.
            return new Spot(eye.x(), eye.y() + CLOSEST, eye.z());
        }
        double push = CLOSEST / away;
        return new Spot(eye.x() + dx * push, eye.y() + dy * push, eye.z() + dz * push);
    }

    /** That many font pixels, in blocks. */
    public static double blocks(double pixels) {
        return pixels * PER_PIXEL;
    }

    /**
     * How far from its anchor any part of a label can end up, in blocks.
     * <p>For the box a renderer is culled against. The box has to hold the writing rather
     * than the block, or standing beside the desk and looking up at its label takes the whole
     * label away - the block leaves the frustum and its renderer goes with it. It also has to
     * hold the standoff above, which moves the writing toward the player and therefore off
     * the block by up to that much again.
     * <p>Generous rather than measured: this is asked during culling, once per block per
     * frame, and a box larger than it needs to be costs a renderer that draws two lines of
     * text nothing at all.
     */
    public static double reach() {
        return CLOSEST + Math.max(
                blocks(WIDEST_PIXELS) / 2,
                blocks((double) LINE_PIXELS * MOST_LINES));
    }
}
