package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How far apart flat things on the table in the world are drawn, against the depth buffer. */
class FlatLayersTest {

    /**
     * Standing at a table, eyes a little over half a block above its top: at four blocks that
     * is a look along the felt about eight degrees steep.
     */
    private static double standingSine(double distance) {
        return 0.6 / Math.hypot(distance, 0.6);
    }

    @Test
    @DisplayName("one step is well clear of the depth buffer from a chair to ten blocks away")
    void clearOfTheBuffer() {
        for (double distance = 0.5; distance <= 10.0; distance += 0.25) {
            double sine = standingSine(distance);
            assertThat(FlatLayers.step(distance))
                    .as("at %.2f blocks", distance)
                    .isGreaterThan(FlatLayers.resolvable(distance, sine) * 1.5);
        }
    }

    @Test
    @DisplayName("the step grows with distance and never past the largest")
    void growsAndStops() {
        double previous = 0;
        for (double distance = 0; distance <= 64; distance += 0.5) {
            double step = FlatLayers.step(distance);
            assertThat(step).isGreaterThanOrEqualTo(previous).isLessThanOrEqualTo(FlatLayers.FARTHEST_STEP);
            previous = step;
        }
        assertThat(FlatLayers.step(0)).isEqualTo(FlatLayers.NEAREST_STEP);
        assertThat(FlatLayers.step(FlatLayers.CLOSE)).isEqualTo(FlatLayers.NEAREST_STEP);
    }

    @Test
    @DisplayName("up close a whole ladder of layers is thinner than a sixteenth of a texel")
    void invisibleUpClose() {
        assertThat(FlatLayers.step(1.5) * 16).isLessThan(1.0 / 16 / 16);
    }

    @Test
    @DisplayName("a card in a pile clears everything drawn on the card under it")
    void aCardClearsTheOneUnder() {
        double step = FlatLayers.step(8);
        assertThat(FlatLayers.perCard(step, 0.0015)).isGreaterThanOrEqualTo(step * FlatLayers.STEPS_PER_CARD);
        assertThat(FlatLayers.perCard(FlatLayers.NEAREST_STEP, 0.0015)).isEqualTo(0.0015);
    }
}
