package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The geometry every screen is built out of.
 * <p>Most of it is arithmetic too plain to be worth a test. The turned hit-test is not: it
 * decides what a click on a table full of overlapping, angled cards actually reaches, and
 * getting the sign of the rotation backwards produces a table that works perfectly at zero
 * and ninety degrees and is subtly wrong at every angle in between.
 */
class RectTest {

    private static final Rect CARD = new Rect(100, 100, 40, 56);

    @Nested
    @DisplayName("turned hit-testing")
    class TurnedHitTesting {

        @Test
        @DisplayName("an upright card is hit exactly where an unturned one is")
        void zeroDegreesIsThePlainTest() {
            assertThat(CARD.containsTurned(0, 101, 101)).isTrue();
            assertThat(CARD.containsTurned(0, 99, 101)).isFalse();
            assertThat(CARD.containsTurned(360, 101, 101)).isTrue();
        }

        @Test
        @DisplayName("the center is on the card at every angle, because turning is about the center")
        void theCenterNeverMoves() {
            for (int degrees = 0; degrees < 360; degrees += 7) {
                assertThat(CARD.containsTurned(degrees, 120, 128))
                        .describedAs("center at %s degrees", degrees)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("a tapped card covers what is beside it and not what is above it")
        void aQuarterTurnSwapsTheAxes() {
            // Turned ninety degrees, the card is 56 wide and 40 tall about the same center.
            assertThat(CARD.containsTurned(90, 145, 128)).isTrue();
            assertThat(CARD.containsTurned(90, 120, 105)).isFalse();
            assertThat(CARD.contains(145, 128)).isFalse();
            assertThat(CARD.contains(120, 105)).isTrue();
        }

        @Test
        @DisplayName("the corner of the bounding box is table, not card")
        void aDiagonalCardDoesNotFillItsBox() {
            // At forty-five degrees the old corners point up, down, left and right, so the
            // corner of the axis-aligned box the card used to fill is now empty table - and a
            // click there has to reach whatever is underneath.
            assertThat(CARD.containsTurned(45, CARD.x() + 1, CARD.y() + 1)).isFalse();
            assertThat(CARD.containsTurned(45, 120, 105)).isTrue();
        }

        @Test
        @DisplayName("turning clockwise is not the same as turning counterclockwise")
        void theDirectionOfTheTurnIsTheScreensDirection() {
            // Every other case here is symmetric about one of the card's own axes, which
            // means they all pass just as happily with the rotation running backwards - and a
            // table whose clicks land on the mirror image of where the cards are drawn is
            // exactly the kind of wrong that looks fine until somebody angles a card.
            //
            // This point sits up and to the right of the center, on the card once it has been
            // turned forty degrees clockwise and off it when turned the other way. Positive
            // degrees are clockwise on screen, matching the y-down pose the renderer turns
            // the card in.
            int pointX = (int) CARD.centerX() + 24;
            int pointY = (int) CARD.centerY() - 10;

            assertThat(CARD.contains(pointX, pointY)).isFalse();
            assertThat(CARD.containsTurned(40, pointX, pointY)).isTrue();
            assertThat(CARD.containsTurned(-40, pointX, pointY)).isFalse();
        }

        @Test
        @DisplayName("an empty rectangle is never hit, however it is turned")
        void nothingIsNeverHit() {
            assertThat(Rect.NONE.containsTurned(30, 0, 0)).isFalse();
        }
    }

    @Property(tries = 2000)
    void turningAllTheWayRoundIsTheSameAsNotTurning(
            @ForAll @IntRange(min = 60, max = 200) int pointX,
            @ForAll @IntRange(min = 60, max = 200) int pointY,
            @ForAll @IntRange(min = -4, max = 4) int turns) {
        assertThat(CARD.containsTurned(turns * 360, pointX, pointY))
                .isEqualTo(CARD.contains(pointX, pointY));
    }

    @Property(tries = 2000)
    void aTurnedCardCoversAsMuchTableAsAnUprightOne(
            @ForAll @IntRange(min = 0, max = 359) int degrees) {
        // Turning a card does not make it bigger or smaller. Counting the pixels it covers is
        // a blunt way to say so, and it is the assertion that catches a hit-test that has
        // quietly started testing the bounding box instead of the card.
        int covered = 0;
        for (int x = 60; x < 200; x++) {
            for (int y = 60; y < 200; y++) {
                if (CARD.containsTurned(degrees, x, y)) {
                    covered++;
                }
            }
        }
        int area = CARD.width() * CARD.height();
        assertThat(covered)
                .describedAs("pixels covered at %s degrees", degrees)
                .isBetween(area - area / 8, area + area / 8);
    }
}
