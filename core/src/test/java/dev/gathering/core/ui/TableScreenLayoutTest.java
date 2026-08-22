package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seated view, laid out at every window size anybody could have.
 *
 * <p>This is the screen a game is played in, so the failure this guards against is not
 * cosmetic: a hand drawn under the action bar is cards you cannot pick up, and a surface with
 * no squares on it is a board you cannot put anything on.
 */
class TableScreenLayoutTest {

    @Property(tries = 3000)
    void nothingLeavesTheScreen(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);

        for (Rect rect : all(layout)) {
            if (rect.isEmpty()) {
                continue;
            }
            assertThat(rect.x()).isGreaterThanOrEqualTo(0);
            assertThat(rect.y()).isGreaterThanOrEqualTo(0);
            assertThat(rect.right()).isLessThanOrEqualTo(width);
            assertThat(rect.bottom()).isLessThanOrEqualTo(height);
        }
    }

    @Property(tries = 3000)
    void theBandsNeverOverlap(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        // A hand drawn under the action bar is cards you cannot pick up.
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);
        Rect[] bands = {layout.opponents(), layout.surface(), layout.hand(), layout.actions()};

        for (int first = 0; first < bands.length; first++) {
            for (int second = first + 1; second < bands.length; second++) {
                assertThat(bands[first].overlaps(bands[second]))
                        .describedAs("band %s overlaps band %s at %sx%s", first, second, width, height)
                        .isFalse();
            }
        }
        assertThat(layout.zones().overlaps(layout.surface())).isFalse();
    }

    @Property(tries = 3000)
    void thereIsAlwaysSomewhereToPutACard(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height,
            @ForAll @IntRange(min = 0, max = 7) int opponents) {
        TableScreenLayout layout = TableScreenLayout.of(width, height, opponents);

        assertThat(layout.visibleSquares()).isPositive();
        assertThat(layout.squareWidth()).isPositive();
        assertThat(layout.squareHeight()).isPositive();
        assertThat(layout.hand().isEmpty()).isFalse();
    }

    @Property(tries = 3000)
    void everySquareIsOnTheSurfaceAndFindableAgain(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        // Drawing a card at a square and then working out which square the cursor is over are
        // two pieces of arithmetic that have to agree, or you drop cards next to where you
        // aimed.
        TableScreenLayout layout = TableScreenLayout.of(width, height, 3);

        for (int column = 0; column < layout.columns(); column++) {
            for (int row = 0; row < layout.rows(); row++) {
                Rect square = layout.squareAt(column, row);
                assertThat(layout.surface().overlaps(square)).isTrue();

                int[] found = layout.squareOf(square.x() + square.width() / 2,
                        square.y() + square.height() / 2);
                assertThat(found).describedAs("square %s,%s is not findable", column, row).isNotNull();
                assertThat(found).containsExactly(column, row);
            }
        }
    }

    @Test
    @DisplayName("the smallest window Minecraft allows still gives you a table and a hand")
    void theSmallestWindowIsStillPlayable() {
        TableScreenLayout layout = TableScreenLayout.of(320, 240, 3);

        assertThat(layout.visibleSquares()).isGreaterThanOrEqualTo(3);
        assertThat(layout.hand().height()).isGreaterThan(20);
    }

    private static Rect[] all(TableScreenLayout layout) {
        return new Rect[] {
            layout.opponents(), layout.surface(), layout.zones(), layout.hand(), layout.actions(),
        };
    }
}
