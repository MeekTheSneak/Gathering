package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class ShakingTest {

    @Test
    void itStartsAtFullStrengthAndIsOverWhenItIsOver() {
        assertThat(Shaking.strength(0)).isEqualTo(1f);
        assertThat(Shaking.strength(Shaking.LASTS)).isEqualTo(0f);
        assertThat(Shaking.strength(Shaking.LASTS * 2)).isEqualTo(0f);
    }

    @Test
    void aShakeThatHasNotStartedIsNotShaking() {
        assertThat(Shaking.strength(-1)).isEqualTo(0f);
        assertThat(Shaking.wobble(0, -1, 4)).isZero();
    }

    @Property
    void itOnlyEverDiesAway(@ForAll @LongRange(min = 0, max = 400) long since) {
        assertThat(Shaking.strength(since))
                .isGreaterThanOrEqualTo(Shaking.strength(Math.min(400, since + 20)));
    }

    /** A pile that shakes further than it was told to would leave its own slot. */
    @Property
    void itNeverMovesFurtherThanItWasAllowed(
            @ForAll @LongRange(min = 0, max = 400) long since,
            @ForAll @IntRange(min = 0, max = 40) int reach,
            @ForAll @IntRange(min = 0, max = 8) int seed) {
        assertThat(Math.abs(Shaking.wobble(seed, since, reach))).isLessThanOrEqualTo(reach);
    }

    @Property
    void nothingMovesOnceTheShakeIsOver(
            @ForAll @LongRange(min = 380, max = 4000) long since,
            @ForAll @IntRange(min = 0, max = 8) int seed) {
        assertThat(Shaking.wobble(seed, since, 6)).isZero();
    }

    /** Two piles shaken at the same instant should not move as though bolted together. */
    @Test
    void twoPilesShakenAtOnceDoNotMoveAsOne() {
        boolean everDiffer = false;
        for (long since = 0; since < Shaking.LASTS; since += 10) {
            if (Shaking.wobble(0, since, 6) != Shaking.wobble(3, since, 6)) {
                everDiffer = true;
                break;
            }
        }
        assertThat(everDiffer).isTrue();
    }
}
