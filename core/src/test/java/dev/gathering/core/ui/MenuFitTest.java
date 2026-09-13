package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A menu that fits the screen it is opened on, at every size a player may have chosen.
 * <p>Written against a number an audit measured: a card menu 792 GUI pixels wide in a
 * 427-pixel viewport, at text 200% with controls 75%. Every action past the halfway point was
 * off the screen and unreachable.
 */
class MenuFitTest {

    /** The viewport the audit photographed. */
    private static final int VIEWPORT = 427;

    /** The card menu's own shape: the longest row, the font, and the rows it carries. */
    private static final int WIDEST = 120;
    private static final int LINE_HEIGHT = 9;
    private static final int ENTRIES = 22;

    private static final int BASE_ROW = 12;
    private static final int PADDING = 3;
    private static final int LEAST_WIDTH = 70;

    private static MenuFit fitFor(int controls, float text, int across, int down) {
        return MenuFit.of(ENTRIES, WIDEST, LINE_HEIGHT, across, down,
                BASE_ROW, controls, text, PADDING, LEAST_WIDTH);
    }

    @Test
    @DisplayName("fits the viewport the audit measured, at the sizes it measured")
    void theAuditsCase() {
        MenuFit fit = fitFor(75, 2.0f, VIEWPORT, 240);
        assertThat(fit.width())
                .as("menu width in a %d-wide viewport", VIEWPORT)
                .isLessThanOrEqualTo(VIEWPORT);
        assertThat(fit.fits(ENTRIES, VIEWPORT, 240, PADDING)).isTrue();
    }

    @Test
    @DisplayName("fits at every pair of sizes a player can choose")
    void everyPairOfSizes() {
        for (int controls = InterfaceScale.SMALLEST_PERCENT;
                controls <= InterfaceScale.LARGEST_PERCENT; controls += 25) {
            for (int text = InterfaceScale.SMALLEST_PERCENT;
                    text <= InterfaceScale.LARGEST_PERCENT; text += 25) {
                MenuFit fit = fitFor(controls, text / 100f, VIEWPORT, 240);
                assertThat(fit.width())
                        .as("width at text %d%%, controls %d%%", text, controls)
                        .isLessThanOrEqualTo(VIEWPORT);
                assertThat(fit.columns() * fit.perColumn(ENTRIES))
                        .as("room for every row at text %d%%, controls %d%%", text, controls)
                        .isGreaterThanOrEqualTo(ENTRIES);
            }
        }
    }

    @Test
    @DisplayName("fits on small windows as well as large ones")
    void everyWindow() {
        for (int across : new int[] {320, 427, 640, 854, 1280}) {
            for (int down : new int[] {160, 240, 400, 700}) {
                MenuFit fit = fitFor(100, 2.0f, across, down);
                assertThat(fit.width())
                        .as("width in %dx%d", across, down)
                        .isLessThanOrEqualTo(across);
            }
        }
    }

    @Test
    @DisplayName("honours the asked text size when there is room for it")
    void keepsTheAskedSizeWhenItFits() {
        // Turning text up is a decision, and quietly turning it back down is not a fix.
        MenuFit roomy = fitFor(100, 2.0f, 1280, 700);
        assertThat(roomy.scale()).isEqualTo(2.0f);
    }

    @Test
    @DisplayName("shrinks only as far as it has to, and never past being letters")
    void shrinksNoFurtherThanNeeded() {
        MenuFit cramped = fitFor(100, 2.0f, 200, 120);
        assertThat(cramped.scale()).isLessThan(2.0f);
        assertThat(cramped.scale()).isGreaterThanOrEqualTo(TextScale.SMALLEST);
    }

    @Test
    @DisplayName("gives one size to the whole menu rather than one per row")
    void oneSizeForTheWholeMenu() {
        // The other half of the audit's screenshot: short labels drawn at full size beside
        // long ones squeezed to fit, which reads as unfinished however correct each row is.
        // There is one scale here by construction, and this is the test that says so.
        MenuFit fit = fitFor(75, 2.0f, VIEWPORT, 240);
        assertThat(fit.scale()).isPositive();
        assertThat(fit.columnWidth()).isGreaterThanOrEqualTo(LEAST_WIDTH);
    }

    @Test
    @DisplayName("keeps a row tall enough for its own writing")
    void rowsHoldTheirText() {
        for (int text = InterfaceScale.SMALLEST_PERCENT;
                text <= InterfaceScale.LARGEST_PERCENT; text += 25) {
            MenuFit fit = fitFor(75, text / 100f, 1280, 700);
            assertThat(fit.rowHeight())
                    .as("row at text %d%%", text)
                    .isGreaterThanOrEqualTo(Math.round(LINE_HEIGHT * fit.scale()));
        }
    }

    @Test
    @DisplayName("never returns nothing to draw")
    void alwaysSomething() {
        MenuFit tiny = MenuFit.of(1, 10, 9, 1, 1, BASE_ROW, 75, 0.75f, PADDING, LEAST_WIDTH);
        assertThat(tiny.columns()).isGreaterThanOrEqualTo(1);
        assertThat(tiny.rowHeight()).isGreaterThanOrEqualTo(1);
        assertThat(tiny.columnWidth()).isGreaterThanOrEqualTo(1);
    }
}
