package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one panel that has to work at every size, because it is the panel that sets the size.
 */
class SettingsLayoutTest {

    private static final int ROWS = 10;

    @Test
    @DisplayName("keeps every row inside the panel, at every control size")
    void rowsStayInside() {
        for (int scale = InterfaceScale.SMALLEST_PERCENT;
                    scale <= InterfaceScale.LARGEST_PERCENT; scale += 25) {
            SettingsLayout layout = SettingsLayout.of(854, 480, ROWS, scale);
            for (int row = 0; row < ROWS; row++) {
                assertThat(layout.row(row).y())
                        .as("row %d top at scale %d", row, scale)
                        .isGreaterThanOrEqualTo(layout.panel().y());
                assertThat(layout.row(row).bottom())
                        .as("row %d bottom at scale %d", row, scale)
                        .isLessThanOrEqualTo(layout.panel().bottom());
            }
        }
    }

    @Test
    @DisplayName("keeps the way out inside the panel, at every control size")
    void thewayOutIsAlwaysReachable() {
        // The one row that must never be pushed off: a panel you cannot close is a trap, and
        // this is the panel somebody opens when something is already too big to use.
        for (int scale = InterfaceScale.SMALLEST_PERCENT;
                    scale <= InterfaceScale.LARGEST_PERCENT; scale += 25) {
            for (int height : new int[] {200, 300, 480, 720, 1080}) {
                SettingsLayout layout = SettingsLayout.of(854, height, ROWS, scale);
                assertThat(layout.wayOut().bottom())
                        .as("way out at scale %d, height %d", scale, height)
                        .isLessThanOrEqualTo(layout.panel().bottom());
                assertThat(layout.wayOut().y())
                        .isGreaterThanOrEqualTo(layout.panel().y());
            }
        }
    }

    @Test
    @DisplayName("fits a small window by shortening rows rather than overflowing")
    void shortWindowsShrinkRows() {
        SettingsLayout roomy = SettingsLayout.of(854, 1080, ROWS, 100);
        SettingsLayout cramped = SettingsLayout.of(854, 260, ROWS, 100);
        assertThat(cramped.rowHeight()).isLessThan(roomy.rowHeight());
        assertThat(cramped.rowHeight()).isGreaterThanOrEqualTo(SettingsLayout.SMALLEST_ROW);
    }

    @Test
    @DisplayName("gives larger rows when larger controls are asked for")
    void controlScaleIsHonored() {
        SettingsLayout small = SettingsLayout.of(854, 1080, ROWS, InterfaceScale.SMALLEST_PERCENT);
        SettingsLayout large = SettingsLayout.of(854, 1080, ROWS, InterfaceScale.LARGEST_PERCENT);
        assertThat(large.rowHeight()).isGreaterThan(small.rowHeight());
    }

    @Test
    @DisplayName("stays on a narrow window")
    void narrowWindowsFit() {
        SettingsLayout layout = SettingsLayout.of(320, 480, ROWS, 100);
        assertThat(layout.panel().x()).isGreaterThanOrEqualTo(0);
        assertThat(layout.panel().right()).isLessThanOrEqualTo(320);
    }

    @Test
    @DisplayName("never lets two rows sit on top of one another")
    void rowsDoNotOverlap() {
        SettingsLayout layout = SettingsLayout.of(854, 480, ROWS, 100);
        for (int row = 1; row < ROWS; row++) {
            assertThat(layout.row(row).y())
                    .isGreaterThanOrEqualTo(layout.row(row - 1).bottom());
        }
    }

    /** A row holds its own words, whatever the controls are set to, while there is room. */
    @Test
    @DisplayName("keeps rows tall enough for large text with small controls")
    void rowsHoldLargeText() {
        SettingsLayout layout = SettingsLayout.of(854, 1080, ROWS, InterfaceScale.SMALLEST_PERCENT, 9, 2f);
        assertThat(layout.rowHeight()).isGreaterThanOrEqualTo(18);
    }

    /** Rows that cannot fit at their shortest stop short of the way out rather than running under it. */
    @Test
    @DisplayName("never lays a row under the way out, however many rows in however small a window")
    void rowsNeverRunUnderTheWayOut() {
        for (int rows : new int[] {9, 11, 14}) {
            SettingsLayout layout = SettingsLayout.of(427, 240, rows, 100, 9, 1f);
            assertThat(layout.row(rows - 1).bottom())
                    .as("%d rows", rows)
                    .isLessThanOrEqualTo(layout.wayOut().y());
        }
    }
}
