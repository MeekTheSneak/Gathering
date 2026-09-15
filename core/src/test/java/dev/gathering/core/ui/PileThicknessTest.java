package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How tall a pile of cards stands on the table in the world. */
class PileThicknessTest {

    @Test
    @DisplayName("an empty pile has no height, and a single card has some")
    void emptyAndOne() {
        assertThat(PileThickness.of(0, 1.0)).isZero();
        assertThat(PileThickness.of(-3, 1.0)).isZero();
        assertThat(PileThickness.of(1, 1.0)).isPositive();
    }

    @Test
    @DisplayName("a pile grows with every card until the tallest, then stops")
    void growsThenStops() {
        double previous = 0;
        for (int cards = 1; cards <= PileThickness.TALLEST; cards++) {
            double height = PileThickness.of(cards, 1.0);
            assertThat(height).as("%d cards", cards).isGreaterThan(previous);
            previous = height;
        }
        assertThat(PileThickness.of(250, 1.0)).isEqualTo(PileThickness.of(PileThickness.TALLEST, 1.0));
    }

    @Test
    @DisplayName("a sixty-card deck stands close to a real one's proportions: under half a card's width")
    void aDeckLooksLikeADeck() {
        double deck = PileThickness.of(60, 1.0);
        assertThat(deck).isBetween(0.3, 0.5);
        // A hundred cards never stands taller than four fifths of a card is wide.
        assertThat(PileThickness.of(PileThickness.TALLEST, 1.0)).isLessThan(0.8);
    }

    @Test
    @DisplayName("measured against the card, so a pile keeps its shape at any table size")
    void scalesWithTheCard() {
        assertThat(PileThickness.of(40, 0.2)).isCloseTo(PileThickness.of(40, 1.0) * 0.2, within(1e-12));
    }

    @Test
    @DisplayName("the side is banded every few cards, and a thin pile is one band")
    void bands() {
        assertThat(PileThickness.bands(1)).isEqualTo(1);
        assertThat(PileThickness.bands(9)).isEqualTo(1);
        assertThat(PileThickness.bands(10)).isEqualTo(2);
        assertThat(PileThickness.bands(60)).isEqualTo(12);
        assertThat(PileThickness.bands(500)).isEqualTo(PileThickness.TALLEST / PileThickness.CARDS_PER_BAND);
    }
}
