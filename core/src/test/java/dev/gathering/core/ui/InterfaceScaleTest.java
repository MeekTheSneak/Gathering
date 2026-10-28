package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How big the interface may be, and how tall a row has to be to hold its writing.
 * <p>The second of those is here because an audit photographed a card menu with its rows drawn
 * through one another at text 200% and controls 75% - a combination that is not unusual and not
 * a misuse: somebody who needs large text often does not want large controls.
 */
class InterfaceScaleTest {

    /** The font the interface is built around. */
    private static final int LINE_HEIGHT = 9;

    private static final int BASE_ROW = 12;
    private static final int PADDING = 3;

    @Test
    @DisplayName("never returns a row shorter than its own text, at any pair of sizes")
    void aRowAlwaysHoldsItsText() {
        for (int controls = InterfaceScale.SMALLEST_PERCENT;
                controls <= InterfaceScale.LARGEST_PERCENT; controls += 5) {
            for (int text = InterfaceScale.SMALLEST_PERCENT;
                    text <= InterfaceScale.LARGEST_PERCENT; text += 5) {
                float textScale = text / 100f;
                int row = InterfaceScale.rowHeightFor(
                        BASE_ROW, LINE_HEIGHT, controls, textScale, PADDING);
                assertThat(row)
                        .as("row at text %d%%, controls %d%%", text, controls)
                        .isGreaterThanOrEqualTo(Math.round(LINE_HEIGHT * textScale));
            }
        }
    }

    @Test
    @DisplayName("is the combination the audit photographed, and it fits")
    void theCombinationFromTheAudit() {
        // Text 200%, controls 75%: the control scale alone would have given a row of 9 for
        // text 18 tall, which is what drew one row through the next.
        int row = InterfaceScale.rowHeightFor(BASE_ROW, LINE_HEIGHT, 75, 2.0f, PADDING);
        assertThat(row).isGreaterThanOrEqualTo(18);
    }

    @Test
    @DisplayName("grows when either size grows")
    void eitherScaleGrowsIt() {
        int small = InterfaceScale.rowHeightFor(BASE_ROW, LINE_HEIGHT, 75, 0.75f, PADDING);
        assertThat(InterfaceScale.rowHeightFor(BASE_ROW, LINE_HEIGHT, 200, 0.75f, PADDING))
                .isGreaterThan(small);
        assertThat(InterfaceScale.rowHeightFor(BASE_ROW, LINE_HEIGHT, 75, 2.0f, PADDING))
                .isGreaterThan(small);
    }

    @Test
    @DisplayName("keeps a percentage inside the range whichever way it is out of it")
    void clamps() {
        assertThat(InterfaceScale.sane(0)).isEqualTo(InterfaceScale.SMALLEST_PERCENT);
        assertThat(InterfaceScale.sane(10_000)).isEqualTo(InterfaceScale.LARGEST_PERCENT);
        assertThat(InterfaceScale.sane(100)).isEqualTo(100);
    }

    @Test
    @DisplayName("never returns nothing at all")
    void neverZero() {
        assertThat(InterfaceScale.rowHeightFor(0, 0, 75, 0f, 0)).isGreaterThanOrEqualTo(1);
    }
}
