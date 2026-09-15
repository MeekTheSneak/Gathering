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
        float[][] strips = FrameStrips.of(left, top, right, bottom, edge);
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
}
