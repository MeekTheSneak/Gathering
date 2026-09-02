package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountersLayoutTest {

    /** The smallest window Minecraft will give you, in GUI-scaled units. */
    private static final int NARROWEST = 320;
    private static final int SHORTEST = 240;

    /** How many counter names a table can put on the button grid. */
    private static final int MOST_BUTTONS = 9;

    @Test
    @DisplayName("a roomy window shows the whole window of counters and every button")
    void roomy() {
        CountersLayout layout = CountersLayout.of(640, 480, 8, MOST_BUTTONS, 0, 0);

        assertThat(layout.counterRows()).isEqualTo(CountersLayout.MAX_ROWS);
        assertThat(layout.commonRows()).isEqualTo(3);
        assertThat(layout.damage()).isEqualTo(Rect.NONE);
    }

    @Test
    @DisplayName("a crowded Commander table on a small window still has its way out")
    void crowdedAndSmall() {
        // Three opponents fielding partners: six commanders to record damage from, which is
        // more than the panel has room for at this size. It used to lay them all out anyway
        // and push the add-a-counter field and the Done button off the bottom of the screen.
        CountersLayout layout = CountersLayout.of(NARROWEST, SHORTEST, 8, MOST_BUTTONS, 6, 0);

        assertThat(layout.done().bottom()).isLessThanOrEqualTo(SHORTEST);
        assertThat(layout.custom().bottom()).isLessThanOrEqualTo(layout.done().y());
        assertThat(layout.damageRow(layout.damageRows() - 1).bottom())
                .isLessThanOrEqualTo(layout.custom().y());
        assertThat(layout.damageRows()).isGreaterThanOrEqualTo(1);
    }

    @Property
    @Label("things give way in the order that costs the player least")
    void sectionsGiveWayInOrder(
            @ForAll @IntRange(min = SHORTEST, max = 720) int height,
            @ForAll @IntRange(min = 0, max = 12) int counters,
            @ForAll @IntRange(min = 0, max = 8) int opponents) {
        CountersLayout layout =
                CountersLayout.of(NARROWEST, height, counters, MOST_BUTTONS, opponents, 0);

        // The counter list keeps three rows while there is still a button row that could have
        // gone instead: every one of those buttons is a name the text field below still takes.
        if (layout.counterRows() < Math.min(counters, CountersLayout.KEEP_ROWS)) {
            assertThat(layout.commonRows()).isZero();
        }
        // And the commander grid, which has neither a wheel nor a shortcut, only loses a row
        // once there is nothing else left to lose.
        if (layout.damageRows() < opponents) {
            assertThat(layout.commonRows()).isZero();
            assertThat(layout.counterRows()).isZero();
        }
    }

    @Property
    @Label("the way out and the way to add a counter are always on screen")
    void footerIsAlwaysReachable(
            @ForAll @IntRange(min = NARROWEST, max = 3840) int width,
            @ForAll @IntRange(min = SHORTEST, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 24) int counters,
            @ForAll @IntRange(min = 0, max = MOST_BUTTONS) int buttons,
            @ForAll @IntRange(min = 0, max = 8) int opponents) {
        CountersLayout layout = CountersLayout.of(width, height, counters, buttons, opponents, 0);

        assertThat(layout.done().bottom()).isLessThanOrEqualTo(height);
        assertThat(layout.done().y()).isGreaterThanOrEqualTo(0);
        assertThat(layout.custom().bottom()).isLessThanOrEqualTo(layout.done().y());
        assertThat(layout.custom().y()).isGreaterThanOrEqualTo(0);
        assertThat(layout.panel().bottom()).isLessThanOrEqualTo(height);
        assertThat(layout.panel().right()).isLessThanOrEqualTo(width);
    }

    @Property
    @Label("nothing the panel lays out reaches down into the footer")
    void bodyStaysAboveTheFooter(
            @ForAll @IntRange(min = SHORTEST, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 24) int counters,
            @ForAll @IntRange(min = 0, max = MOST_BUTTONS) int buttons,
            @ForAll @IntRange(min = 0, max = 8) int grid,
            @ForAll boolean asTax) {
        CountersLayout layout = CountersLayout.of(NARROWEST, height, counters, buttons,
                asTax ? 0 : grid, asTax ? grid : 0);

        int floor = layout.custom().y();
        assertThat(layout.counterFooter().bottom()).isLessThanOrEqualTo(floor);
        for (int index = 0; index < layout.commonRows() * CountersLayout.BUTTON_COLUMNS; index++) {
            assertThat(layout.commonButton(index).bottom()).isLessThanOrEqualTo(floor);
        }
        for (int index = 0; index < layout.damageRows(); index++) {
            assertThat(layout.damageRow(index).bottom()).isLessThanOrEqualTo(floor);
        }
        for (int index = 0; index < layout.taxRows(); index++) {
            assertThat(layout.taxRow(index).bottom()).isLessThanOrEqualTo(floor);
        }
    }

    @Property
    @Label("the panel never shows more rows than it was asked for")
    void neverInventsRows(
            @ForAll @IntRange(min = 0, max = 24) int counters,
            @ForAll @IntRange(min = 0, max = MOST_BUTTONS) int buttons,
            @ForAll @IntRange(min = 0, max = 8) int opponents) {
        CountersLayout layout = CountersLayout.of(640, 480, counters, buttons, opponents, 0);

        assertThat(layout.counterRows()).isLessThanOrEqualTo(counters);
        assertThat(layout.commonRows() * CountersLayout.BUTTON_COLUMNS)
                .isLessThanOrEqualTo(buttons + CountersLayout.BUTTON_COLUMNS - 1);
        assertThat(layout.damageRows()).isLessThanOrEqualTo(opponents);
    }
}
