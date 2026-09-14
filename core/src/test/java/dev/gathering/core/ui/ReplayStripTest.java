package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The replay transport: what is drawn is what is clicked, at any window size. */
class ReplayStripTest {

    private static final int COUNT = 60;

    private static ReplayStrip along(int width, int height) {
        return new ReplayStrip(TableScreenLayout.watching(width, height).hand(), COUNT);
    }

    @Test
    @DisplayName("each button's own middle presses that button")
    void buttonsAnswerForThemselves() {
        ReplayStrip strip = along(854, 480);
        ReplayStrip.Target[] expected = {ReplayStrip.Target.START, ReplayStrip.Target.BACK,
                ReplayStrip.Target.PLAY_PAUSE, ReplayStrip.Target.ON};
        for (int index = 0; index < ReplayStrip.BUTTONS; index++) {
            Rect button = strip.button(index);
            assertThat(strip.at((int) button.centerX(), button.y() + button.height() / 2))
                    .as("button %d", index).isEqualTo(expected[index]);
        }
    }

    @Test
    @DisplayName("the buttons, the bar and the count never overlap, and all sit inside the strip")
    void nothingOverlaps() {
        ReplayStrip strip = along(854, 480);
        for (int index = 1; index < ReplayStrip.BUTTONS; index++) {
            assertThat(strip.button(index).x()).isGreaterThanOrEqualTo(strip.button(index - 1).right());
        }
        assertThat(strip.bar().x()).isGreaterThan(strip.button(ReplayStrip.BUTTONS - 1).right());
        assertThat(strip.countX()).isGreaterThan(strip.bar().right());
        assertThat(strip.countX() + COUNT).isLessThanOrEqualTo(strip.strip().right());
        assertThat(strip.bar().y()).isGreaterThanOrEqualTo(strip.strip().y());
        assertThat(strip.bar().bottom()).isLessThanOrEqualTo(strip.strip().bottom());
    }

    @Test
    @DisplayName("the whole height of the strip answers for the bar, not just the ruler")
    void theStripIsTheBar() {
        ReplayStrip strip = along(854, 480);
        Rect bar = strip.bar();
        int x = bar.x() + bar.width() / 2;
        assertThat(strip.at(x, strip.strip().y())).isEqualTo(ReplayStrip.Target.BAR);
        assertThat(strip.at(x, strip.strip().bottom() - 1)).isEqualTo(ReplayStrip.Target.BAR);
        assertThat(strip.at(x, strip.strip().y() - 1)).isEqualTo(ReplayStrip.Target.NOTHING);
    }

    @Test
    @DisplayName("a drag off either end of the bar holds at the first or last step")
    void dragsClamp() {
        ReplayStrip strip = along(854, 480);
        assertThat(strip.stepUnder(-500, 340)).isZero();
        assertThat(strip.stepUnder(5000, 340)).isEqualTo(340);
        assertThat(strip.stepUnder(strip.bar().x() + strip.bar().width() / 2, 340)).isEqualTo(170);
        assertThat(strip.stepUnder(400, 0)).isZero();
    }

    @Property(tries = 500)
    void scrubbingToAPointFillsTheBarToThatPoint(
            @ForAll @IntRange(min = 200, max = 3000) int width,
            @ForAll @IntRange(min = 1, max = 2000) int steps,
            @ForAll @IntRange(min = 0, max = 1000) int permille) {
        // Clicking somewhere on the bar and then drawing the step it chose must put the fill's
        // edge back under the cursor, give or take a step. A fill that ran ahead or behind the
        // click is a scrubber that seems to slip.
        ReplayStrip strip = along(width, 480);
        Rect bar = strip.bar();
        int x = bar.x() + (int) Math.round(bar.width() * permille / 1000.0);
        int step = strip.stepUnder(x, steps);
        int edge = bar.x() + strip.filled(step, steps);
        double oneStep = bar.width() / (double) steps;
        assertThat((double) Math.abs(edge - x)).isLessThanOrEqualTo(oneStep + 1);
    }
}
