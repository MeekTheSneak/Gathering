package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The colors deck boxes come in.
 * <p>What is worth pinning is not the numbers but the two properties the feature rests on: a
 * deck keeps its color, and two decks rarely share one. Both fail silently - a box that
 * changed color would look like a different deck, and a wheel that collapsed to three hues
 * would look like it was working.
 */
class DeckColorsTest {

    @Test
    @DisplayName("the same deck is the same color every time")
    void aColorIsStable() {
        assertThat(DeckColors.pick(42L)).isEqualTo(DeckColors.pick(42L));
        assertThat(DeckColors.pick("Mono Red".hashCode()))
                .isEqualTo(DeckColors.pick("Mono Red".hashCode()));
    }

    @Test
    @DisplayName("every color is opaque, so a box is never half invisible")
    void everyColorIsOpaque() {
        for (long seed = 0; seed < 200; seed++) {
            assertThat(DeckColors.pick(seed) >>> 24)
                    .describedAs("alpha of seed %s", seed)
                    .isEqualTo(0xFF);
        }
    }

    @Test
    @DisplayName("the whole wheel is reachable, and nothing outside it is")
    void theWheelIsUsedWhole() {
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 5_000; seed++) {
            seen.add(DeckColors.pick(seed));
        }
        assertThat(seen)
                .describedAs("five thousand decks came out in %s colors", seen.size())
                .hasSize(DeckColors.HUES);
    }

    /**
     * Consecutive seeds are not neighboring colors.
     * <p>The case this exists for: decks named "Deck 1" and "Deck 2" hash to consecutive
     * numbers, and without mixing they would come out as the two hues beside each other -
     * two boxes a player is meant to tell apart, in the two colors hardest to.
     */
    @Test
    @DisplayName("decks with neighboring seeds are not neighboring colors")
    void consecutiveSeedsScatter() {
        int same = 0;
        for (long seed = 0; seed < 100; seed++) {
            if (DeckColors.pick(seed) == DeckColors.pick(seed + 1)) {
                same++;
            }
        }
        assertThat(same)
                .describedAs("consecutive seeds landed on the same color %s times in a hundred", same)
                .isLessThan(15);
    }

    @Test
    @DisplayName("no color is so dark or so pale that a white box reads as unpainted")
    void everyColorIsReadable() {
        for (long seed = 0; seed < 500; seed++) {
            int color = DeckColors.pick(seed);
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;
            int brightest = Math.max(red, Math.max(green, blue));
            int dimmest = Math.min(red, Math.min(green, blue));
            assertThat(brightest).describedAs("brightest channel of seed %s", seed)
                    .isGreaterThan(180);
            assertThat(brightest - dimmest).describedAs("saturation of seed %s", seed)
                    .isGreaterThan(60);
        }
    }
}
