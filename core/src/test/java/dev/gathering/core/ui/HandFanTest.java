package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The hand along the bottom of the screen.
 *
 * <p>A hand has no fixed size, which is the whole difficulty: everything here has to hold for
 * one card and for twenty, and the ways it goes wrong are all at the ends of that range. A big
 * hand that keeps its cards side by side runs off the screen; one that shrinks them to fit
 * turns them into confetti; one that overlaps without limit leaves slivers nobody can pick.
 */
class HandFanTest {

    private static final Rect AREA = new Rect(0, 400, 854, 80);

    @Nested
    @DisplayName("however many cards there are")
    class WhateverTheSize {

        @Property(tries = 400)
        void theFanStaysOnTheScreen(@ForAll @IntRange(min = 1, max = 30) int count) {
            for (int index = 0; index < count; index++) {
                Rect where = HandFan.slot(AREA, count, index, -1).where();
                assertThat(where.x())
                        .describedAs("card %s of %s runs off the left", index, count)
                        .isGreaterThanOrEqualTo(AREA.x() - 4);
                assertThat(where.right())
                        .describedAs("card %s of %s runs off the right", index, count)
                        .isLessThanOrEqualTo(AREA.right() + 4);
            }
        }

        @Property(tries = 600)
        void everyCardKeepsAShowingEdge(
                @ForAll @IntRange(min = 2, max = 60) int count,
                @ForAll @IntRange(min = 320, max = 1920) int width) {
            // The failure this exists for: a hand large enough that the cards overlap into
            // slivers and picking the one you want stops being possible. Across window widths
            // as well as hand sizes, because a fan that only crowds on a small screen looks
            // fine right up until somebody windows the game.
            Rect area = new Rect(0, 400, width, 80);
            Rect first = HandFan.slot(area, count, 0, -1).where();
            int step = HandFan.slot(area, count, 1, -1).where().x() - first.x();

            assertThat(step)
                    .describedAs("showing edge of each card, %s cards across %s", count, width)
                    .isGreaterThanOrEqualTo((int) (first.width() * 0.2));
        }

        @Test
        @DisplayName("a huge hand in the smallest window still leaves an edge of every card")
        void theWorstCaseIsPinnedDown() {
            // The corner the property above kept missing. Sixty cards in a 320-wide window is
            // where the overlap floor and the width of the strip actually fight, and a search
            // over two dimensions went six hundred tries without landing on it - so it goes in
            // by hand. A property that only fails in one corner needs that corner nailed down.
            Rect narrow = new Rect(0, 400, 320, 80);
            Rect first = HandFan.slot(narrow, 60, 0, -1).where();
            int step = HandFan.slot(narrow, 60, 1, -1).where().x() - first.x();

            assertThat(step)
                    .describedAs("showing edge of each card, 60 cards across 320")
                    .isGreaterThanOrEqualTo((int) (first.width() * 0.2));
            assertThat(HandFan.slot(narrow, 60, 59, -1).where().right())
                    .describedAs("and the last of them is still on the screen")
                    .isLessThanOrEqualTo(narrow.right() + 4);
        }

        @Property(tries = 400)
        void anOrdinaryHandIsDrawnLargeEnoughToRead(@ForAll @IntRange(min = 1, max = 15) int count) {
            // Up to a grip of fifteen the cards should still be a decent size; the shrinking
            // is for the Windfall case, not for a normal turn.
            Rect first = HandFan.slot(AREA, count, 0, -1).where();

            assertThat(first.width()).isGreaterThan(20);
            assertThat(first.height()).isGreaterThan(28);
        }

        @Property(tries = 400)
        void cardsRunLeftToRightInOrder(@ForAll @IntRange(min = 2, max = 30) int count) {
            for (int index = 1; index < count; index++) {
                assertThat(HandFan.slot(AREA, count, index, -1).where().x())
                        .describedAs("card %s of %s", index, count)
                        .isGreaterThan(HandFan.slot(AREA, count, index - 1, -1).where().x());
            }
        }

        @Property(tries = 400)
        void aCardIsAlwaysCardShaped(@ForAll @IntRange(min = 1, max = 30) int count) {
            for (int index = 0; index < count; index++) {
                Rect where = HandFan.slot(AREA, count, index, -1).where();
                assertThat(where.width() / (double) where.height())
                        .isCloseTo(488.0 / 680.0, org.assertj.core.data.Offset.offset(0.03));
            }
        }
    }

    @Nested
    @DisplayName("picking one out")
    class Picking {

        @Property(tries = 400)
        void everyCardCanBeFoundAtItsOwnMiddle(@ForAll @IntRange(min = 1, max = 30) int count) {
            // Not every point of every card - they overlap, so most of each one is behind the
            // next. What has to hold is that the part still showing belongs to it.
            for (int index = 0; index < count; index++) {
                Rect where = HandFan.slot(AREA, count, index, -1).where();
                int found = HandFan.at(AREA, count, where.x() + 3, (int) Math.round(where.centreY()));
                assertThat(found)
                        .describedAs("the showing edge of card %s of %s", index, count)
                        .isEqualTo(index);
            }
        }

        @Test
        @DisplayName("the last card is the front one where they overlap")
        void laterCardsAreInFront() {
            Rect last = HandFan.slot(AREA, 6, 5, -1).where();

            assertThat(HandFan.at(AREA, 6, (int) Math.round(last.centreX()), (int) Math.round(last.centreY()))).isEqualTo(5);
        }

        @Test
        @DisplayName("a point off the fan is not a card")
        void nothingIsNothing() {
            assertThat(HandFan.at(AREA, 5, AREA.x() + 1, AREA.y() - 40)).isEqualTo(-1);
            assertThat(HandFan.at(AREA, 0, 400, 440)).isEqualTo(-1);
        }

        @Test
        @DisplayName("the space a lifted card would rise into is not part of the fan")
        void theLiftZoneIsNotHittable() {
            // The flicker this prevents: hit-testing against the drawn shapes means a card
            // grows the moment it is picked, which changes what the cursor is over, which
            // un-picks it, which shrinks it back. Asserting the answer twice does not catch
            // that - it is the same call, and it passed with the bug deliberately in. What
            // catches it is a point only inside a card once that card has risen: from the
            // fan's point of view that space has to be nothing at all.
            Rect resting = HandFan.slot(AREA, 7, 3, -1).where();
            Rect lifted = HandFan.slot(AREA, 7, 3, 3).where();
            int aboveTheFan = (lifted.y() + resting.y()) / 2;

            assertThat(aboveTheFan)
                    .describedAs("a point the lifted card covers and the resting one does not")
                    .isLessThan(resting.y());
            assertThat(HandFan.at(AREA, 7, (int) Math.round(resting.centreX()), aboveTheFan))
                    .isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("the card under the cursor")
    class Lifted {

        @Test
        @DisplayName("comes up out of the fan, bigger and straight")
        void theLiftedCardIsBiggerAndUpright() {
            HandFan.Slot resting = HandFan.slot(AREA, 7, 5, -1);
            HandFan.Slot lifted = HandFan.slot(AREA, 7, 5, 5);

            assertThat(lifted.where().height()).isGreaterThan(resting.where().height());
            assertThat(lifted.where().y()).isLessThan(resting.where().y());
            assertThat(lifted.angle()).describedAs("read straight, not at the fan's angle").isZero();
        }

        @Test
        @DisplayName("stays over the card it replaced rather than jumping sideways")
        void theLiftedCardKeepsItsPlace() {
            HandFan.Slot resting = HandFan.slot(AREA, 9, 2, -1);
            HandFan.Slot lifted = HandFan.slot(AREA, 9, 2, 2);

            assertThat(lifted.where().centreX())
                    .isCloseTo(resting.where().centreX(), org.assertj.core.data.Offset.offset(2.0));
        }

        @Test
        @DisplayName("lifting one card leaves the others where they were")
        void theRestOfTheFanHoldsStill() {
            for (int index = 0; index < 8; index++) {
                if (index == 4) {
                    continue;
                }
                assertThat(HandFan.slot(AREA, 8, index, 4))
                        .describedAs("card %s while card 4 is lifted", index)
                        .isEqualTo(HandFan.slot(AREA, 8, index, -1));
            }
        }
    }

    @Test
    @DisplayName("the fan is centred, so a small hand sits in the middle rather than off to one side")
    void aSmallHandIsCentred() {
        Rect only = HandFan.slot(AREA, 1, 0, -1).where();

        assertThat(only.centreX()).isCloseTo(AREA.centreX(), org.assertj.core.data.Offset.offset(2.0));
    }
}
