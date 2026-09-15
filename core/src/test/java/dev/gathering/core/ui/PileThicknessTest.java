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

    @Test
    @DisplayName("every side of a pile faces out of it, so the world does not cull the near walls")
    void sidesFaceOutward() {
        for (int side = 0; side < PileThickness.SIDES; side++) {
            double[][] corners = PileThickness.sideCorners(side, 0.3, 0.4, 0.0, 0.1);
            double[] along = minus(corners[1], corners[0]);
            double[] up = minus(corners[2], corners[1]);
            // Counterclockwise seen from outside is a cross product pointing out.
            double[] facing = {
                    along[1] * up[2] - along[2] * up[1],
                    along[2] * up[0] - along[0] * up[2],
                    along[0] * up[1] - along[1] * up[0]};
            int[] normal = PileThickness.sideNormal(side);
            assertThat(facing[0] * normal[0] + facing[2] * normal[1]).as("side %d", side).isPositive();
            assertThat(facing[1]).as("side %d is upright", side).isZero();
            // And it lies on the side it says: every corner out at that edge.
            for (double[] corner : corners) {
                assertThat(corner[0] * normal[0] + corner[2] * normal[1])
                        .isEqualTo(normal[0] != 0 ? 0.3 : 0.4, within(1e-9));
            }
        }
    }

    private static double[] minus(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
}
