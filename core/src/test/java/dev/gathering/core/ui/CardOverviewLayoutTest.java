package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class CardOverviewLayoutTest {

    @Property
    @Label("on any window, nothing of the overview overlaps anything else or leaves the window")
    void nothingOverlapsOrLeaves(
            @ForAll @IntRange(min = 240, max = 3840) int width,
            @ForAll @IntRange(min = 180, max = 2160) int height,
            @ForAll @IntRange(min = 18, max = 40) int row) {
        CardOverviewLayout layout = CardOverviewLayout.of(width, height, row);
        Rect[] parts = {layout.card(), layout.words(), layout.history(), layout.buttons()};
        for (Rect part : parts) {
            assertThat(part.x()).isGreaterThanOrEqualTo(0);
            assertThat(part.y()).isGreaterThanOrEqualTo(0);
            assertThat(part.right()).isLessThanOrEqualTo(width);
            assertThat(part.bottom()).isLessThanOrEqualTo(height);
        }
        for (int one = 0; one < parts.length; one++) {
            for (int two = one + 1; two < parts.length; two++) {
                if (!parts[one].isEmpty() && !parts[two].isEmpty()) {
                    assertThat(parts[one].overlaps(parts[two])).as("part %s and part %s", one, two).isFalse();
                }
            }
        }
    }

    @Property
    @Label("the card keeps a card's shape")
    void theCardIsCardShaped(
            @ForAll @IntRange(min = 320, max = 3840) int width,
            @ForAll @IntRange(min = 240, max = 2160) int height) {
        Rect card = CardOverviewLayout.of(width, height, 20).card();
        assertThat(card.isEmpty()).isFalse();
        assertThat((double) card.height() / card.width()).isBetween(1.3, 1.5);
    }
}
