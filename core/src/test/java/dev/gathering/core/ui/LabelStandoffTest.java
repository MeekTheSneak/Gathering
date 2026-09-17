package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.ui.LabelStandoff.Spot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The label over a table or a desk, at every distance a player can stand at.
 * <p>Reported as "getting close to a table or standings desk with a display above it, portions
 * of the display phase out of existence and it becomes difficult to read". Written at a fixed
 * scale in world units, a line two blocks wide is four windows across from half a block away,
 * and most of it is off the screen.
 */
class LabelStandoffTest {

    /** Where a desk's label belongs: the middle of the block, over the top of it. */
    private static final Spot ANCHOR = new Spot(10.5, 2.4, 10.5);

    @Test
    @DisplayName("leaves the writing where it belongs from any ordinary distance")
    void farAwayNothingMoves() {
        for (double away = LabelStandoff.CLOSEST; away < 64; away += 0.25) {
            Spot eye = new Spot(ANCHOR.x() + away, ANCHOR.y(), ANCHOR.z());
            assertThat(LabelStandoff.keptBack(ANCHOR, eye))
                    .as("at %.2f blocks", away)
                    .isEqualTo(ANCHOR);
        }
    }

    @Test
    @DisplayName("never lets the writing come closer than the standoff, from any side")
    void closeUpItBacksOff() {
        for (double away = 0.01; away < LabelStandoff.CLOSEST; away += 0.01) {
            for (int step = 0; step < 6; step++) {
                // A unit direction, so the eye really is that far off rather than roughly so:
                // round the block, and from below it, level with it and above it.
                double round = Math.PI * step / 3;
                double up = Math.PI / 6 * (step % 3 - 1);
                double flat = Math.cos(up);
                Spot eye = new Spot(
                        ANCHOR.x() + flat * Math.cos(round) * away,
                        ANCHOR.y() + Math.sin(up) * away,
                        ANCHOR.z() + flat * Math.sin(round) * away);
                Spot drawn = LabelStandoff.keptBack(ANCHOR, eye);
                assertThat(distance(drawn, eye))
                        .as("eye %.2f blocks away, %d", away, step)
                        .isCloseTo(LabelStandoff.CLOSEST, within());
            }
        }
    }

    @Test
    @DisplayName("keeps the writing in the direction it belongs in, only further off")
    void itBacksOffAlongTheSameLine() {
        Spot eye = new Spot(ANCHOR.x() - 0.4, ANCHOR.y() - 0.9, ANCHOR.z());
        Spot drawn = LabelStandoff.keptBack(ANCHOR, eye);
        double wasX = ANCHOR.x() - eye.x();
        double wasY = ANCHOR.y() - eye.y();
        double nowX = drawn.x() - eye.x();
        double nowY = drawn.y() - eye.y();
        // Same direction: the cross product of the two offsets is zero, and both point the
        // same way rather than opposite ways.
        assertThat(wasX * nowY - wasY * nowX).isCloseTo(0, within());
        assertThat(wasX * nowX + wasY * nowY).isPositive();
    }

    @Test
    @DisplayName("has an answer for standing exactly where the writing is")
    void standingInsideIt() {
        Spot drawn = LabelStandoff.keptBack(ANCHOR, ANCHOR);
        assertThat(distance(drawn, ANCHOR)).isCloseTo(LabelStandoff.CLOSEST, within());
        assertThat(drawn.y()).isGreaterThan(ANCHOR.y());
    }

    @Test
    @DisplayName("asks for a culling box that holds the widest label and the standoff too")
    void theBoxHoldsTheWriting() {
        assertThat(LabelStandoff.reach())
                .isGreaterThan(LabelStandoff.blocks(LabelStandoff.WIDEST_PIXELS) / 2)
                .isGreaterThan(LabelStandoff.CLOSEST);
    }

    private static double distance(Spot one, Spot other) {
        double dx = one.x() - other.x();
        double dy = one.y() - other.y();
        double dz = one.z() - other.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(1e-9);
    }
}
