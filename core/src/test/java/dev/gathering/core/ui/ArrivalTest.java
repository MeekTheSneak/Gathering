package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How a pile glows after cards land in it. */
class ArrivalTest {

    @Test
    @DisplayName("full as the cards land, fading to nothing, and nothing before they land or after")
    void fades() {
        assertThat(Arrival.strength(-1, false)).isZero();
        assertThat(Arrival.strength(0, false)).isEqualTo(1f);
        float previous = 1f;
        for (long since = 50; since < Arrival.LASTS; since += 50) {
            float now = Arrival.strength(since, false);
            assertThat(now).as("%d ms", since).isLessThan(previous).isPositive();
            previous = now;
        }
        assertThat(Arrival.strength(Arrival.LASTS, false)).isZero();
    }

    @Test
    @DisplayName("with reduced motion it holds steady for as long, then goes")
    void holdsSteadyForLessMotion() {
        assertThat(Arrival.strength(0, true)).isEqualTo(Arrival.STEADY);
        assertThat(Arrival.strength(Arrival.LASTS - 1, true)).isEqualTo(Arrival.STEADY);
        assertThat(Arrival.strength(Arrival.LASTS, true)).isZero();
        assertThat(Arrival.strength(-5, true)).isZero();
    }

    @Test
    @DisplayName("never solid, so the pile under the glow still shows")
    void neverSolid() {
        assertThat(Arrival.alpha(1f)).isLessThan(0xFF).isPositive();
        assertThat(Arrival.alpha(0f)).isZero();
        assertThat(Arrival.alpha(2f)).isEqualTo(Arrival.alpha(1f));
    }
}
