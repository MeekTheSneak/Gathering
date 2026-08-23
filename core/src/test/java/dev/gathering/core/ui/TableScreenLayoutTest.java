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
    @DisplayName("a lifted card rises out of the strip and is still on the screen")
    void aLiftedCardRisesOverTheTable() {
        // It grows upward over the felt rather than inside its own strip. Keeping room for it
        // there instead is the obvious thing and costs every resting card a third of its size
        // for a space that is empty almost all the time - so the strip is a card deep, and the
        // one card that grows is drawn over the table, where nothing clips it.
        TableScreenLayout layout = TableScreenLayout.of(854, 480);
        Rect resting = HandFan.slot(layout.hand(), 7, 3, -1).where();
        Rect lifted = HandFan.slot(layout.hand(), 7, 3, 3).where();

        assertThat(lifted.y()).describedAs("rises above the strip").isLessThan(layout.hand().y());
        assertThat(lifted.height()).isGreaterThan(resting.height());
        assertThat(lifted.y()).describedAs("and not off the top of the screen").isGreaterThan(0);
    }

    @Test
    @DisplayName("the smallest window Minecraft allows still gives you a hand")
    void theSmallestWindowIsStillPlayable() {
        TableScreenLayout layout = TableScreenLayout.of(320, 240);

        assertThat(layout.hand().height()).isGreaterThan(40);
        assertThat(layout.hand().overlaps(layout.status())).isFalse();
    }
}
