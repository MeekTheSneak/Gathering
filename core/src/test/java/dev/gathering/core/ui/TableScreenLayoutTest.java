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
 * <p>Almost nothing, now that the table is the screen: a hand along the bottom and a thin
 * strip of names along the top. What is left to get wrong is where those meet the table, and
 * it is worth getting right - a click that counts as both your hand and the felt underneath it
 * picks a card up and puts it down in the same gesture.
 */
class TableScreenLayoutTest {

    @Property(tries = 3000)
    void theHandAndTheStripNeverOverlap(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        assertThat(layout.hand().overlaps(layout.status()))
                .describedAs("hand over the status strip at %sx%s", width, height)
                .isFalse();
    }

    @Property(tries = 3000)
    void bothStayOnTheScreen(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        for (Rect rect : new Rect[] {layout.hand(), layout.status()}) {
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
        assertThat(layout.hand().height()).isGreaterThan(40);
    }

    @Property(tries = 3000)
    void nothingOverTheHandOrTheStripCountsAsTable(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);

        assertThat(layout.isOnFelt(width / 2, layout.hand().y() + 1)).isFalse();
        assertThat(layout.isOnFelt(width / 2, layout.status().y() + 1)).isFalse();
        assertThat(layout.isOnFelt(width / 2, layout.status().bottom() + 1)).isTrue();
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
    @DisplayName("the hand is deeper than a card, so one can rise out of the fan")
    void thereIsRoomToLiftACard() {
        // The fan draws the card under the cursor larger and higher than the rest. If the
        // strip were only as deep as a card, that one would be clipped by the top of it -
        // which is exactly the card the player is trying to read.
        TableScreenLayout layout = TableScreenLayout.of(854, 480);
        Rect biggest = HandFan.slot(layout.hand(), 7, 3, 3).where();

        assertThat(biggest.y())
                .describedAs("a lifted card starts inside the strip it belongs to")
                .isGreaterThanOrEqualTo(layout.hand().y());
    }

    @Test
    @DisplayName("the smallest window Minecraft allows still gives you a hand")
    void theSmallestWindowIsStillPlayable() {
        TableScreenLayout layout = TableScreenLayout.of(320, 240);

        assertThat(layout.hand().height()).isGreaterThan(40);
        assertThat(layout.hand().overlaps(layout.status())).isFalse();
    }
}
