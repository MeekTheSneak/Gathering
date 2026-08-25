package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The wide draw, which real print sheets turned out to need. */
class DeterministicRandomTest {

    private static final byte[] SEED = "not a real seed".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("a bound an int can hold is drawn exactly as it always was")
    void smallBoundsDrawTheSameStreamAsBefore() {
        DeterministicRandom asInts = DeterministicRandom.forLabel(SEED, "same");
        DeterministicRandom asLongs = DeterministicRandom.forLabel(SEED, "same");
        for (int draw = 0; draw < 32; draw++) {
            assertThat(asLongs.nextLong(1000)).isEqualTo(asInts.nextInt(1000));
        }
    }

    @Test
    @DisplayName("a wide draw reaches both sides of what an int could hold")
    void wideDrawsReachPastTheIntMark() {
        // Two halves either side of the int mark. A draw assembled from a high and a low int -
        // which this once was - could only ever land in the first.
        long bound = 4L * Integer.MAX_VALUE;
        DeterministicRandom rolls = DeterministicRandom.forLabel(SEED, "wide");
        boolean below = false;
        boolean above = false;
        for (int draw = 0; draw < 200; draw++) {
            long value = rolls.nextLong(bound);
            assertThat(value).isBetween(0L, bound - 1);
            below |= value < Integer.MAX_VALUE;
            above |= value > 3L * Integer.MAX_VALUE;
        }
        assertThat(below).as("nothing came up in the first quarter").isTrue();
        assertThat(above).as("nothing came up in the last quarter").isTrue();
    }

    @Test
    @DisplayName("wide draws spread evenly across the range")
    void wideDrawsAreSpreadEvenly() {
        long bound = 210_395_225_040L;
        DeterministicRandom rolls = DeterministicRandom.forLabel(SEED, "even");
        int[] buckets = new int[8];
        int draws = 4000;
        for (int draw = 0; draw < draws; draw++) {
            buckets[(int) (rolls.nextLong(bound) / (bound / 8 + 1))]++;
        }
        // A wide margin on purpose: this is checking that no eighth is starved or doubled,
        // which is what every wrong version of this draw did, not that the stream is perfect.
        for (int bucket : buckets) {
            assertThat(bucket).isBetween(draws / 8 / 2, draws / 8 * 2);
        }
    }

    @Test
    @DisplayName("the same seed and label draw the same wide numbers")
    void wideDrawsAreReproducible() {
        assertThat(draws("wide")).isEqualTo(draws("wide"));
        assertThat(draws("wide")).isNotEqualTo(draws("other"));
    }

    @Test
    void anEmptyRangeIsRefused() {
        DeterministicRandom rolls = DeterministicRandom.forLabel(SEED, "none");
        assertThatThrownBy(() -> rolls.nextLong(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rolls.nextLong(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Long> draws(String label) {
        DeterministicRandom rolls = DeterministicRandom.forLabel(SEED, label);
        List<Long> drawn = new ArrayList<>();
        for (int draw = 0; draw < 16; draw++) {
            drawn.add(rolls.nextLong(1_000_000_000_000L));
        }
        return drawn;
    }
}
