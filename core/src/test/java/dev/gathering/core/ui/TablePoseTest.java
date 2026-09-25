package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A body at the table.
 * <p>The owner's constraint on this whole feature was one sentence - the arm must not leave the
 * shoulder - and the property below is that sentence. Everything else here is about the poses
 * that look wrong rather than the ones that are impossible: an arm folded through the chest, a
 * head screwed round backwards, a fan of forty cards drawn as a wheel.
 */
class TablePoseTest {

    @Nested
    @net.jqwik.api.Group
    @DisplayName("reach")
    class Reach {

        @Property
        @Label("the hand never leaves the shoulder, wherever the target is")
        void theHandStaysOnTheBody(
                @ForAll @DoubleRange(min = -16, max = 16) double across,
                @ForAll @DoubleRange(min = -16, max = 16) double forward,
                @ForAll @DoubleRange(min = -4, max = 4) double down) {
            double[] hand = TablePose.handAt(across, forward, down);

            double length = Math.sqrt(
                    hand[0] * hand[0] + hand[1] * hand[1] + hand[2] * hand[2]);
            assertThat(length).isLessThanOrEqualTo(TablePose.ARM_REACH);
        }

        @Property
        @Label("a target already within reach is not moved at all")
        void whatIsCloseIsLeftAlone(
                @ForAll @DoubleRange(min = -0.3, max = 0.3) double across,
                @ForAll @DoubleRange(min = -0.3, max = 0.3) double forward,
                @ForAll @DoubleRange(min = -0.3, max = 0.3) double down) {
            double[] hand = TablePose.handAt(across, forward, down);

            assertThat(hand[0]).isCloseTo(across, within(1e-9));
            assertThat(hand[1]).isCloseTo(forward, within(1e-9));
            assertThat(hand[2]).isCloseTo(down, within(1e-9));
        }

        @Test
        @DisplayName("pointing across a whole pod still points that way")
        void theDirectionSurvivesTheClamp() {
            // Five blocks across a four-table cluster, which is the case the clamp is for: the arm
            // cannot get there, and it must still be aimed at it rather than given up on.
            //
            // Against something straight ahead, not against a nearer target in the same direction:
            // the arm's yaw is a direction, so it does not change with distance, and comparing two
            // points on one line asks nothing. The first version of this did exactly that and went
            // on passing when the decomposition beneath it was replaced.
            TablePose.Aim far = TablePose.reaching(5.0, 1.0, 0.4);
            TablePose.Aim ahead = TablePose.reaching(0.0, 1.0, 0.4);

            assertThat(far.armYaw()).isGreaterThan(ahead.armYaw());
            assertThat(far.armYaw()).isGreaterThan(45f);
        }
    }

    @Nested
    @net.jqwik.api.Group
    @DisplayName("what a body will not do")
    class WhatABodyWillNotDo {

        @Property
        @Label("the arm never folds through the chest")
        void theArmStaysOutOfTheBody(
                @ForAll @DoubleRange(min = -16, max = 16) double across,
                @ForAll @DoubleRange(min = -16, max = 16) double forward,
                @ForAll @DoubleRange(min = -4, max = 4) double down) {
            TablePose.Aim aim = TablePose.reaching(across, forward, down);

            assertThat(aim.armYaw()).isGreaterThanOrEqualTo(-45f);
            assertThat(aim.armYaw()).isLessThanOrEqualTo(95f);
            assertThat(aim.armPitch()).isBetween(0f, 100f);
        }

        @Property
        @Label("the head turns as far as a neck goes and no further")
        void theHeadStaysOnTheNeck(
                @ForAll @DoubleRange(min = -16, max = 16) double across,
                @ForAll @DoubleRange(min = -16, max = 16) double forward,
                @ForAll @DoubleRange(min = -4, max = 4) double down) {
            TablePose.Aim aim = TablePose.reaching(across, forward, down);

            assertThat(aim.headYaw()).isBetween(-70f, 70f);
            assertThat(aim.headPitch()).isBetween(-30f, 80f);
        }

        @Test
        @DisplayName("nobody pointing anywhere is a body at rest")
        void restingIsResting() {
            assertThat(TablePose.Aim.RESTING.armYaw()).isZero();
            assertThat(TablePose.Aim.RESTING.armPitch()).isZero();
            assertThat(TablePose.Aim.RESTING.headYaw()).isZero();
            assertThat(TablePose.Aim.RESTING.headPitch()).isZero();
        }
    }

    @Nested
    @net.jqwik.api.Group
    @DisplayName("reaching a table from a chair")
    class ReachingATable {

        /** A table's felt, measured from its own block, as {@code TableTop} has it. */
        private static final double FELT_ABOVE_ITS_BLOCK = 15.02 / 16.0;

        @Test
        @DisplayName("a shoulder is above the felt, not below it")
        void theShoulderIsAboveTheTable() {
            // The defect this whole class was pulled out of the renderer for: the shoulder was put
            // at hip height, the felt was above it, and every body at a table looked at the
            // ceiling. A player sits no lower than the block their table stands on.
            assertThat(Shoulder.belowTheShoulder(0, FELT_ABOVE_ITS_BLOCK)).isGreaterThan(0);
        }

        @Test
        @DisplayName("pointing at the table in front of you looks down at it")
        void theHeadLooksDownAtTheTable() {
            // Facing south, a table a block away, its felt below the shoulder.
            TablePose.Aim aim = Shoulder.toward(
                    0, 0, 0, 0, true, 0, FELT_ABOVE_ITS_BLOCK, 1.0);

            assertThat(aim.headPitch()).isGreaterThan(0f);
            assertThat(aim.armPitch()).isGreaterThan(0f);
        }

        @Test
        @DisplayName("the arm swings the way the cursor went")
        void theArmFollowsAcross() {
            TablePose.Aim left = Shoulder.toward(
                    0, 0, 0, 0, true, 1.0, FELT_ABOVE_ITS_BLOCK, 1.0);
            TablePose.Aim right = Shoulder.toward(
                    0, 0, 0, 0, true, -1.0, FELT_ABOVE_ITS_BLOCK, 1.0);

            // Facing south (+z), the player's right hand is to the west (-x). So a target at -x is
            // further to their right than one at +x, and the arm has to turn further out for it.
            assertThat(right.armYaw()).isGreaterThan(left.armYaw());
        }

        @Test
        @DisplayName("a cursor crossing the felt moves the arm across it, not up and down")
        void theArmFollowsTheCursorAcross() {
            // Six places along the near edge of a table, left to right, from a seat facing it.
            // What the owner saw twice was an arm pinned at its across-the-body clamp while only
            // its pitch moved - so this asks for movement in the direction the cursor actually
            // went, which is the thing that was missing rather than any particular angle.
            float[] yaws = new float[6];
            for (int step = 0; step < yaws.length; step++) {
                double across = -0.9 + step * 0.36;
                yaws[step] = Shoulder.toward(
                        0, 0, 0, 0, true, across, FELT_ABOVE_ITS_BLOCK, 0.9).armYaw();
            }
            for (int step = 1; step < yaws.length; step++) {
                assertThat(yaws[step])
                        .as("the arm stopped following at step " + step + " of " + java.util.Arrays.toString(yaws))
                        .isLessThan(yaws[step - 1]);
            }
        }

        @Property
        @Label("a body at a table never looks up at it, wherever on the felt it points")
        void nothingOnATableIsAboveTheShoulder(
                @ForAll @DoubleRange(min = -8, max = 8) double across,
                @ForAll @DoubleRange(min = -8, max = 8) double along,
                @ForAll @DoubleRange(min = 0, max = 360) double bodyYaw) {
            TablePose.Aim aim = Shoulder.toward(
                    0, 0, 0, bodyYaw, true, across, FELT_ABOVE_ITS_BLOCK, along);

            assertThat(aim.headPitch()).isGreaterThanOrEqualTo(0f);
        }
    }

    @Nested
    @net.jqwik.api.Group
    @DisplayName("the fan")
    class TheFan {

        @Test
        @DisplayName("no cards is no fan, rather than a fan of none")
        void anEmptyHandIsEmpty() {
            assertThat(HeldFan.of(0)).isEmpty();
            assertThat(HeldFan.of(-3)).isEmpty();
            assertThat(HeldFan.widthOf(0)).isZero();
        }

        @Test
        @DisplayName("one card is held straight")
        void oneCardIsStraight() {
            List<HeldFan.Card> fan = HeldFan.of(1);

            assertThat(fan).hasSize(1);
            assertThat(fan.get(0).angle()).isZero();
            assertThat(fan.get(0).slide()).isZero();
        }

        @Property
        @Label("a hand is never wider than a hand, however many cards are in it")
        void aHandIsNeverAWheel(@ForAll @IntRange(min = 1, max = 120) int cards) {
            List<HeldFan.Card> fan = HeldFan.of(cards);

            assertThat(fan).hasSize(cards);
            for (HeldFan.Card card : fan) {
                assertThat(Math.abs(card.angle())).isLessThanOrEqualTo(37f);
                assertThat(Math.abs(card.slide())).isLessThanOrEqualTo(1.2f);
            }
        }

        @Property
        @Label("no two cards are coplanar, so nothing z-fights")
        void everyCardHasItsOwnDepth(@ForAll @IntRange(min = 2, max = 60) int cards) {
            List<HeldFan.Card> fan = HeldFan.of(cards);

            for (int at = 1; at < fan.size(); at++) {
                assertThat(fan.get(at).depth()).isGreaterThan(fan.get(at - 1).depth());
            }
        }

        @Property
        @Label("the fan is symmetrical about the middle of the hand")
        void theFanIsSymmetrical(@ForAll @IntRange(min = 2, max = 40) int cards) {
            List<HeldFan.Card> fan = HeldFan.of(cards);

            assertThat(fan.get(0).angle()).isCloseTo(-fan.get(cards - 1).angle(), within(1e-4f));
            assertThat(fan.get(0).slide()).isCloseTo(-fan.get(cards - 1).slide(), within(1e-4f));
        }
    }
}
