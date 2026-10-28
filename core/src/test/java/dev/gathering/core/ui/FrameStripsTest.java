package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A frame's strips cover the frame once, corners included. */
class FrameStripsTest {

    @Test
    @DisplayName("no two strips of a frame overlap, and together they are the whole frame")
    void eachPieceOnce() {
        float left = 1f;
        float top = 2f;
        float right = 11f;
        float bottom = 7f;
        float edge = 0.5f;
        float[][] strips = stripsOf(left, top, right, bottom, edge);
        double area = 0;
        for (int one = 0; one < strips.length; one++) {
            area += (strips[one][2] - strips[one][0]) * (strips[one][3] - strips[one][1]);
            for (int other = one + 1; other < strips.length; other++) {
                double across = Math.min(strips[one][2], strips[other][2]) - Math.max(strips[one][0], strips[other][0]);
                double down = Math.min(strips[one][3], strips[other][3]) - Math.max(strips[one][1], strips[other][1]);
                assertThat(across > 1e-6 && down > 1e-6).as("strips %d and %d overlap", one, other).isFalse();
            }
        }
        double whole = (right - left) * (bottom - top) - (right - left - 2 * edge) * (bottom - top - 2 * edge);
        assertThat(area).isCloseTo(whole, within(1e-4));
    }

    @Test
    @DisplayName("the strips are handed over in the order and at the places they were written out")
    void eachIsTheStripsAsWritten() {
        // The table the strips were written out in before they were handed over one at a time, kept here as the
        // reference: top and bottom the whole width, then the two sides between them.
        float[][] written = {{1f, 2f, 11f, 2.5f}, {1f, 6.5f, 11f, 7f}, {1f, 2.5f, 1.5f, 6.5f}, {10.5f, 2.5f, 11f, 6.5f}};
        assertThat(stripsOf(1f, 2f, 11f, 7f, 0.5f)).isEqualTo(written);
    }

    /** A frame's strips, collected in the order they are handed over. */
    private static float[][] stripsOf(float left, float top, float right, float bottom, float edge) {
        java.util.List<float[]> handed = new java.util.ArrayList<>();
        FrameStrips.each(left, top, right, bottom, edge, (fromX, fromY, toX, toY) -> handed.add(new float[] {fromX, fromY, toX, toY}));
        return handed.toArray(float[][]::new);
    }

    @Test
    @DisplayName("drawing a frame one strip at a time makes no garbage")
    void aFrameMakesNoGarbage() {
        // Every mat, zone, slot and group on a table in the world is a frame, drawn every frame at every table in
        // sight; handed over as an array of strips, it was five arrays each time.
        com.sun.management.ThreadMXBean threads = PileThicknessTest.allocationCounting();
        Area area = new Area();
        for (int round = 0; round < 50_000; round++) {
            FrameStrips.each(1f, 2f, 11f + round % 3, 7f, 0.5f, area);
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int round = 0; round < 100_000; round++) {
            FrameStrips.each(1f, 2f, 11f + round % 3, 7f, 0.5f, area);
        }
        long made = threads.getCurrentThreadAllocatedBytes() - before;
        assertThat(area.sum).isPositive();
        assertThat(made).as("bytes allocated drawing 100,000 frames").isLessThan(16 * 1024);
    }

    /** Adds up what it is handed, so the strips are used and nothing is optimized away. */
    private static final class Area implements FrameStrips.Sink {
        double sum;

        @Override
        public void strip(float left, float top, float right, float bottom) {
            sum += (right - left) * (bottom - top);
        }
    }
}
