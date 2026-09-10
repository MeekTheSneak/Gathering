package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.MagicColor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Five points in a ring, the way the back of a Magic card has them. */
class ColorWheelTest {

    private static final int CENTRE = 100;
    private static final int RADIUS = 50;

    @Nested
    @DisplayName("where the five sit")
    class WhereTheySit {

        @Test
        @DisplayName("starts at the top, not at the right")
        void whiteIsAtTheTop() {
            // Zero radians is to the right, and a pentagon starting there is the right shape
            // rotated into the wrong one. White is at the top on every card back there is.
            ColorWheel.Spoke first = ColorWheel.spoke(0, CENTRE, CENTRE, RADIUS);
            assertThat(first.x()).isEqualTo(CENTRE);
            assertThat(first.y()).isEqualTo(CENTRE - RADIUS);
        }

        @Test
        @DisplayName("goes clockwise, which is the order WUBRG is written in")
        void clockwise() {
            // The second point is to the right of the first and below it: clockwise. Going
            // the other way would put blue where green belongs, which is a pentagon that
            // looks nearly right, and nearly right is worse here than obviously different.
            ColorWheel.Spoke white = ColorWheel.spoke(0, CENTRE, CENTRE, RADIUS);
            ColorWheel.Spoke blue = ColorWheel.spoke(1, CENTRE, CENTRE, RADIUS);
            assertThat(blue.x()).isGreaterThan(white.x());
            assertThat(blue.y()).isGreaterThan(white.y());
        }

        @Test
        @DisplayName("gives five, all the same distance from the middle")
        void aRing() {
            List<ColorWheel.Spoke> ring = ColorWheel.spokes(CENTRE, CENTRE, RADIUS);
            assertThat(ring).hasSize(ColorWheel.SPOKES);
            for (ColorWheel.Spoke spoke : ring) {
                double away = Math.hypot(spoke.x() - CENTRE, spoke.y() - CENTRE);
                // Whole pixels, so a point can be half a pixel off its true radius.
                assertThat(away).isCloseTo(RADIUS, org.assertj.core.data.Offset.offset(1.0));
            }
        }

        @Test
        @DisplayName("has one point per color, in the enum's own order")
        void oneEach() {
            assertThat(ColorWheel.SPOKES).isEqualTo(MagicColor.count());
        }

        @Test
        @DisplayName("wraps an index past the end rather than throwing")
        void wraps() {
            assertThat(ColorWheel.spoke(5, CENTRE, CENTRE, RADIUS))
                    .isEqualTo(ColorWheel.spoke(0, CENTRE, CENTRE, RADIUS));
            assertThat(ColorWheel.spoke(-1, CENTRE, CENTRE, RADIUS))
                    .isEqualTo(ColorWheel.spoke(4, CENTRE, CENTRE, RADIUS));
        }
    }

    @Nested
    @DisplayName("what the cursor is on")
    class WhatTheCursorIsOn {

        @Test
        @DisplayName("finds the point the cursor is in the middle of")
        void deadOn() {
            for (int at = 0; at < ColorWheel.SPOKES; at++) {
                ColorWheel.Spoke spoke = ColorWheel.spoke(at, CENTRE, CENTRE, RADIUS);
                assertThat(ColorWheel.at(spoke.x(), spoke.y(), CENTRE, CENTRE, RADIUS, 12))
                        .isEqualTo(at);
            }
        }

        @Test
        @DisplayName("finds nothing in the empty middle of the ring")
        void theMiddleIsNothing() {
            assertThat(ColorWheel.at(CENTRE, CENTRE, CENTRE, CENTRE, RADIUS, 12)).isEqualTo(-1);
        }

        @Test
        @DisplayName("finds nothing outside the ring")
        void outside() {
            assertThat(ColorWheel.at(CENTRE, CENTRE - RADIUS * 3, CENTRE, CENTRE, RADIUS, 12))
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("is round, so a corner's width outside a point is not on it")
        void roundNotSquare() {
            ColorWheel.Spoke spoke = ColorWheel.spoke(0, CENTRE, CENTRE, RADIUS);
            int reach = 10;
            // Diagonally away by more than the reach, but within a square of that half-width.
            assertThat(ColorWheel.at(spoke.x() + 8, spoke.y() + 8,
                    CENTRE, CENTRE, RADIUS, reach)).isEqualTo(-1);
        }

        @Test
        @DisplayName("takes the nearest when two overlap, not whichever was checked first")
        void nearestWins() {
            // A tiny ring with a huge reach: every point is within reach of every position,
            // and the answer must still be the one actually nearest.
            int tiny = 4;
            ColorWheel.Spoke third = ColorWheel.spoke(2, CENTRE, CENTRE, tiny);
            assertThat(ColorWheel.at(third.x(), third.y(), CENTRE, CENTRE, tiny, 100))
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("fitting in a box")
    class Fitting {

        @Test
        @DisplayName("leaves room for the points themselves")
        void leavesRoom() {
            int radius = ColorWheel.radiusIn(200, 200, 26);
            ColorWheel.Spoke top = ColorWheel.spoke(0, 100, 100, radius);
            // The top of the topmost point is still inside a box from 0 to 200.
            assertThat(top.y() - 26 / 2).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("never goes negative, however small the box")
        void neverNegative() {
            assertThat(ColorWheel.radiusIn(4, 4, 26)).isZero();
            assertThat(ColorWheel.radiusIn(0, 0, 0)).isZero();
        }

        @Test
        @DisplayName("uses the shorter side, so a wide short box still fits")
        void theShorterSide() {
            assertThat(ColorWheel.radiusIn(400, 100, 20))
                    .isEqualTo(ColorWheel.radiusIn(100, 100, 20));
        }
    }
}
