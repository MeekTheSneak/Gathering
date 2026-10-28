package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a card is drawn, as both boards' pickers ask it: four corners, a point on them or not, and the box round them.
 */
class CornersTest {

    @Test
    @DisplayName("a point on a turned card is on it, and one past its edge is not, whichever way round the corners go")
    void aTurnedCardHoldsItsOwnMiddle() {
        Rect card = new Rect(100, 50, 20, 30);
        for (int angle : new int[] {0, 37, 90, 180, 270, 315}) {
            double[][] corners = Corners.of(card, angle);
            double[][] backwards = {corners[3], corners[2], corners[1], corners[0]};
            for (double[][] round : new double[][][] {corners, backwards}) {
                assertThat(Corners.inside(round, card.centerX(), card.centerY())).as("%s degrees", angle).isTrue();
                // Further from the middle than any corner of the card is.
                assertThat(Corners.inside(round, card.centerX() + 19, card.centerY() + 19))
                        .as("%s degrees", angle).isFalse();
                assertThat(Corners.inside(round, card.centerX() - 40, card.centerY())).as("%s degrees", angle).isFalse();
            }
        }
        // Upright, the corners are the rectangle's own, and its edges count as on it.
        double[][] upright = Corners.of(card, 0);
        assertThat(upright[0]).containsExactly(100.0, 50.0);
        assertThat(upright[2]).containsExactly(120.0, 80.0);
        assertThat(Corners.inside(upright, 120, 80)).isTrue();
        assertThat(Corners.inside(upright, 120.5, 80)).isFalse();
    }

    /**
     * A card squeezed to nothing - a pot slot rounded to no pixels at the furthest zoom - is under the cursor nowhere.
     * Asked the usual way, every point is on neither side of an edge that has no length, so it was under the cursor
     * everywhere, and the flat board ringed that card of the pot and gave it to the read key wherever the cursor was.
     */
    @Test
    @DisplayName("a card with no area holds no point")
    void nothingIsOnACardWithNoArea() {
        double[][] point = Corners.of(new Rect(40, 40, 0, 0), 0);
        double[][] line = Corners.of(new Rect(40, 40, 0, 30), 37);
        for (double[] cursor : new double[][] {{40, 40}, {0, 0}, {300, -12}, {40, 55}}) {
            assertThat(Corners.inside(point, cursor[0], cursor[1])).as("%s,%s", cursor[0], cursor[1]).isFalse();
            assertThat(Corners.inside(line, cursor[0], cursor[1])).as("%s,%s", cursor[0], cursor[1]).isFalse();
        }
        double[][] unknown = {{Double.NaN, 0}, {10, 0}, {10, 10}, {0, 10}};
        assertThat(Corners.inside(unknown, 5, 5)).isFalse();
    }

    @Test
    @DisplayName("the box round a card holds every corner of it, and upright it is the card")
    void theBoxRoundACard() {
        Rect card = new Rect(100, 50, 20, 30);
        assertThat(Corners.around(Corners.of(card, 0))).isEqualTo(card);
        for (int angle = 0; angle < 360; angle += 15) {
            Rect box = Corners.around(Corners.of(card, angle));
            for (double[] corner : Corners.of(card, angle)) {
                assertThat(corner[0]).as("%s degrees", angle).isBetween((double) box.x(), (double) box.right());
                assertThat(corner[1]).as("%s degrees", angle).isBetween((double) box.y(), (double) box.bottom());
            }
        }
    }
}
