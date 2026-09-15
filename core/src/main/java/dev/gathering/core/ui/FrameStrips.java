package dev.gathering.core.ui;

/**
 * The four strips a flat frame on the table is drawn with: top and bottom the full width, and the two
 * sides between them.
 * <p>Between them, so no part of the frame is covered twice. The strips were each drawn corner to corner
 * once, which covered every corner twice on one plane: the depth buffer chose between the two per frame,
 * and the corners of the mats, zones and groups on the table in the world flickered.
 * <p>Pure, so that is tested rather than looked for.
 */
public final class FrameStrips {

    private FrameStrips() {
    }

    /** The strips, each as {left, top, right, bottom}: top, bottom, left side, right side. */
    public static float[][] of(float left, float top, float right, float bottom, float edge) {
        return new float[][] {
                {left, top, right, top + edge},
                {left, bottom - edge, right, bottom},
                {left, top + edge, left + edge, bottom - edge},
                {right - edge, top + edge, right, bottom - edge},
        };
    }
}
