package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The verb search's rows hold their writing at every size a player can ask for.
 * <p>Written against what the panel did at text 200%: each label drawn eighteen pixels tall on
 * an eleven-pixel row, through both its neighbors, with a click on its visible top read as a click
 * on the row above. Every check here draws the writing where the board's text drawing puts it -
 * centered on the middle of a full-size line - and asks whether it stays in its own place.
 */
class PaletteLayoutTest {

    /** The game's font. */
    private static final int LINE = 9;

    /** A window, and the strip along its top, at the smallest size the game lays out. */
    private static final int ROOM_DOWN = 240 - 16 - PaletteLayout.PADDING * 2;

    /** Where a line drawn at this call and scale really starts and ends: centered on a full-size line. */
    private static double[] drawn(int drawY, float scale) {
        double top = drawY + (LINE - LINE * scale) / 2.0;
        return new double[] {top, top + LINE * scale};
    }

    @Test
    @DisplayName("every row's writing stays inside its own row, at every text and control size")
    void rowsNeverOverlap() {
        for (int controls = InterfaceScale.SMALLEST_PERCENT; controls <= InterfaceScale.LARGEST_PERCENT; controls += 25) {
            for (int text = InterfaceScale.SMALLEST_PERCENT; text <= InterfaceScale.LARGEST_PERCENT; text += 25) {
                for (boolean acting : new boolean[] {false, true}) {
                    float scale = text / 100f;
                    PaletteLayout layout = PaletteLayout.of(LINE, scale, controls, acting, 40, 1000);
                    double lastBottom = Double.NEGATIVE_INFINITY;
                    for (int row = 0; row < layout.visibleRows(); row++) {
                        int top = layout.rowOffset(row);
                        double[] label = drawn(top + layout.labelDrawOffset(), scale);
                        String at = "row " + row + " at text " + text + "%, controls " + controls + "%";
                        assertThat(label[0]).as("top of the writing on " + at).isGreaterThanOrEqualTo(top);
                        assertThat(label[1]).as("foot of the writing on " + at)
                                .isLessThanOrEqualTo(top + layout.rowHeight());
                        assertThat(label[0]).as(at + " starts below the row above").isGreaterThanOrEqualTo(lastBottom);
                        lastBottom = label[1];
                        // And a click on any of the writing is a click on this row.
                        for (int y = (int) Math.ceil(label[0]); y < label[1]; y++) {
                            assertThat(layout.rowAt(y)).as("a click at " + y + " on the writing of " + at).isEqualTo(row);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("what was typed and what it acts on sit above the first row, and apart")
    void theHeaderStaysAboveTheRows() {
        for (int text = InterfaceScale.SMALLEST_PERCENT; text <= InterfaceScale.LARGEST_PERCENT; text += 25) {
            float scale = text / 100f;
            PaletteLayout layout = PaletteLayout.of(LINE, scale, 100, true, 9, 1000);
            double[] typed = drawn(layout.typedDrawOffset(), scale);
            double[] acting = drawn(layout.actingDrawOffset(), scale);
            String at = " at text " + text + "%";
            // Within half a pixel: a drawing call lands on a whole pixel, and half the growth of an
            // enlarged line is not always one.
            assertThat(typed[0]).as("the typed line inside the panel" + at).isGreaterThanOrEqualTo(PaletteLayout.PADDING - 0.5);
            assertThat(acting[0]).as("the acting line below the typed one" + at).isGreaterThanOrEqualTo(typed[1]);
            assertThat(acting[1]).as("the acting line above the first row" + at)
                    .isLessThanOrEqualTo(layout.firstRowOffset());
            assertThat(layout.rowAt((int) Math.floor(acting[1]) - 1)).as("the acting line is not a row" + at).isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("the panel is never taller than its room while one row fits, and shows every row it has room for")
    void thePanelFitsItsRoom() {
        for (int controls = InterfaceScale.SMALLEST_PERCENT; controls <= InterfaceScale.LARGEST_PERCENT; controls += 25) {
            for (int text = InterfaceScale.SMALLEST_PERCENT; text <= InterfaceScale.LARGEST_PERCENT; text += 25) {
                PaletteLayout layout = PaletteLayout.of(LINE, text / 100f, controls, true, 40, ROOM_DOWN);
                String at = " at text " + text + "%, controls " + controls + "%";
                assertThat(layout.height()).as("panel height" + at).isLessThanOrEqualTo(ROOM_DOWN);
                assertThat(layout.visibleRows()).as("rows shown" + at)
                        .isBetween(1, PaletteLayout.MOST_ROWS);
                assertThat(layout.height() + layout.rowHeight()).as("room for one more row" + at)
                        .satisfiesAnyOf(
                                more -> assertThat(more).isGreaterThan(ROOM_DOWN),
                                more -> assertThat(layout.visibleRows()).isEqualTo(PaletteLayout.MOST_ROWS));
            }
        }
    }

    @Test
    @DisplayName("at full size it is the panel it always was")
    void fullSizeIsUnchanged() {
        PaletteLayout layout = PaletteLayout.of(LINE, 1f, 100, false, 40, 1000);
        assertThat(layout.rowHeight()).isEqualTo(PaletteLayout.BASE_ROW);
        assertThat(layout.firstRowOffset()).isEqualTo(PaletteLayout.PADDING + LINE + PaletteLayout.GAP);
        assertThat(layout.typedDrawOffset()).isEqualTo(PaletteLayout.PADDING);
        assertThat(layout.visibleRows()).isEqualTo(PaletteLayout.MOST_ROWS);
    }
}
