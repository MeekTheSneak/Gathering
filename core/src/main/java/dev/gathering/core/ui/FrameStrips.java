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

    /**
     * The strips, handed over one at a time, each as left, top, right, bottom: top, bottom, left side, right side.
     * <p>One at a time rather than as an array of them, which was five arrays a frame, and a table's frames are
     * drawn every frame.
     */
    public static void each(float left, float top, float right, float bottom, float edge, Sink sink) {
        sink.strip(left, top, right, top + edge);
        sink.strip(left, bottom - edge, right, bottom);
        sink.strip(left, top + edge, left + edge, bottom - edge);
        sink.strip(right - edge, top + edge, right, bottom - edge);
    }

    /** Where a frame's strips go, one at a time. */
    public interface Sink {
        void strip(float left, float top, float right, float bottom);
    }
}
