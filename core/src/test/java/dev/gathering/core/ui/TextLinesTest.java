package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lines of enlarged writing stepped one under another never print through each other.
 * <p>The board's game log stepped ten pixels a line, its table talk eleven and the lesson's heading
 * twelve, at every text size; at 200% each line is eighteen pixels of writing drawn about its own
 * middle. Checked the way the board draws: the writing centered on the middle of a full-size line.
 */
class TextLinesTest {

    private static double[] drawn(int drawY, int lineHeight, float scale) {
        double top = drawY + (lineHeight - lineHeight * scale) / 2.0;
        return new double[] {top, top + lineHeight * scale};
    }

    @Test
    @DisplayName("each line starts where it was put and ends before the next begins, at every size")
    void linesStayInTheirPlaces() {
        for (int lineHeight = 7; lineHeight <= 12; lineHeight++) {
            for (int gap = 0; gap <= 3; gap++) {
                for (float scale = TextScale.SMALLEST; scale <= TextScale.LARGEST + 1e-4f; scale += 0.05f) {
                    TextLines lines = new TextLines(lineHeight, scale, gap);
                    int top = 37;
                    double lastBottom = Double.NEGATIVE_INFINITY;
                    for (int line = 0; line < 6; line++) {
                        double[] box = drawn(lines.drawY(top + line * lines.pitch()), lineHeight, lines.scale());
                        String at = "line " + line + " at " + scale + " with a font of " + lineHeight + " and a gap of " + gap;
                        // Within half a pixel, and a hair for the float arithmetic: a drawing call lands
                        // on a whole pixel, and half an enlarged line's growth is not always one.
                        assertThat(box[0]).as("top of " + at).isGreaterThanOrEqualTo(top + line * lines.pitch() - 0.5 - 1e-4);
                        assertThat(box[1]).as("foot of " + at)
                                .isLessThanOrEqualTo(top + line * lines.pitch() + lines.drawnHeight() + 0.5 + 1e-4);
                        assertThat(box[0]).as(at + " below the line above it").isGreaterThanOrEqualTo(lastBottom - 1e-4);
                        lastBottom = box[1];
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("at full size a line is the font's own height, and a call goes where it always did")
    void fullSizeIsTheFont() {
        TextLines lines = new TextLines(9, 1f, 1);
        assertThat(lines.drawnHeight()).isEqualTo(9);
        assertThat(lines.pitch()).isEqualTo(10);
        assertThat(lines.drawY(20)).isEqualTo(20);
    }

    @Test
    @DisplayName("a scale nobody meant is drawn at a sane one rather than spacing lines a screen apart")
    void aMistakenScaleIsKeptSane() {
        assertThat(new TextLines(9, 400f, 1).scale()).isEqualTo(TextScale.LARGEST);
        assertThat(new TextLines(9, Float.NaN, 1).scale()).isEqualTo(TextScale.SMALLEST);
    }
}
