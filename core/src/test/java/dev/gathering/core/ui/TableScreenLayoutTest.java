package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The furniture that cannot live on the felt.
 *
 * <p>Almost nothing, now that the table is the screen: a hand along the bottom and a bar under
 * it. What is left to get wrong is where those two meet, and it is worth getting right - a
 * hand drawn under the action bar is cards you cannot pick up, and a bar drawn over the hand
 * is a row of cards with a caption through it.
 */
class TableScreenLayoutTest {

    @Property(tries = 3000)
    void theHandAndTheBarNeverOverlap(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        assertThat(layout.hand().overlaps(layout.actions()))
                .describedAs("hand over the action bar at %sx%s", width, height)
                .isFalse();
    }

    @Property(tries = 3000)
    void bothStayOnTheScreen(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        for (Rect rect : new Rect[] {layout.hand(), layout.actions()}) {
            assertThat(rect.x()).isGreaterThanOrEqualTo(0);
            assertThat(rect.y()).isGreaterThanOrEqualTo(0);
            assertThat(rect.right()).isLessThanOrEqualTo(width);
            assertThat(rect.bottom()).isLessThanOrEqualTo(height);
        }
    }

    @Property(tries = 3000)
    void thereIsAlwaysAHandToHold(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        assertThat(layout.hand().isEmpty()).isFalse();
        assertThat(layout.hand().height()).isGreaterThan(20);
    }

    @Property(tries = 3000)
    void nothingOverTheHandOrTheBarCountsAsTable(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // A click on your own hand must not also be a click on the felt underneath it, or
        // picking a card up would drop it on the table in the same gesture.
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        assertThat(layout.isOnFelt(width / 2, layout.hand().y() + 1)).isFalse();
        assertThat(layout.isOnFelt(width / 2, layout.actions().y() + 1)).isFalse();
        assertThat(layout.isOnFelt(width / 2, 1)).isTrue();
    }

    @Test
    @DisplayName("the felt runs the whole screen, under the hand rather than stopping at it")
    void theFeltIsTheScreen() {
        // A table that ended where your cards begin would have a strip you could see across
        // but never put anything on, and panning would keep sliding cards under a lip.
        TableScreenLayout layout = TableScreenLayout.of(854, 480);

        assertThat(layout.felt()).isEqualTo(new Rect(0, 0, 854, 480));
    }

    @Test
    @DisplayName("the smallest window Minecraft allows still gives you a hand")
    void theSmallestWindowIsStillPlayable() {
        TableScreenLayout layout = TableScreenLayout.of(320, 240);

        assertThat(layout.hand().height()).isGreaterThan(20);
        assertThat(layout.hand().overlaps(layout.actions())).isFalse();
    }
}
