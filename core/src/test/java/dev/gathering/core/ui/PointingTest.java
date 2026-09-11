package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The ring around a card somebody has just pointed at. */
class PointingTest {

    @Nested
    @DisplayName("while it lasts")
    class WhileItLasts {

        @Test
        @DisplayName("starts bright, because the beginning is when somebody said 'that one'")
        void startsBright() {
            // A ring that eases in is a ring nobody catches the beginning of.
            assertThat(Pointing.strength(0)).isGreaterThan(0.9f);
        }

        @Test
        @DisplayName("pulses rather than holding steady")
        void pulses() {
            // A steady ring reads as a state the card is in - selected, targeted - rather than
            // as somebody pointing. A pulse reads as a gesture, which is what this is.
            float lowest = 1f;
            float highest = 0f;
            for (long at = 0; at < Pointing.LASTS / 2; at += 10) {
                float now = Pointing.strength(at);
                lowest = Math.min(lowest, now);
                highest = Math.max(highest, now);
            }
            assertThat(highest - lowest).isGreaterThan(0.3f);
        }

        @Test
        @DisplayName("fades as it goes, so the last pulse is the faintest")
        void fades() {
            // Measured as the brightest point in each half: the pulse means any single
            // instant can be dark, so comparing two instants proves nothing.
            assertThat(brightestBetween(0, Pointing.LASTS / 2))
                    .isGreaterThan(brightestBetween(Pointing.LASTS / 2, Pointing.LASTS));
        }

        private float brightestBetween(long from, long to) {
            float best = 0f;
            for (long at = from; at < to; at += 5) {
                best = Math.max(best, Pointing.strength(at));
            }
            return best;
        }

        @Test
        @DisplayName("never goes outside nought and one")
        void staysInRange() {
            for (long at = -50; at < Pointing.LASTS + 200; at += 7) {
                assertThat(Pointing.strength(at)).isBetween(0f, 1f);
            }
        }
    }

    @Nested
    @DisplayName("once it is over")
    class OnceItIsOver {

        @Test
        @DisplayName("is nothing, and draws nothing")
        void nothingAfterwards() {
            assertThat(Pointing.strength(Pointing.LASTS)).isZero();
            assertThat(Pointing.strength(Pointing.LASTS + 1_000)).isZero();
            assertThat(Pointing.thickness(40, Pointing.LASTS)).isZero();
            assertThat(Pointing.color(0xFFCC66, Pointing.LASTS)).isZero();
        }

        @Test
        @DisplayName("is nothing before it began, too")
        void nothingBefore() {
            assertThat(Pointing.strength(-1)).isZero();
            assertThat(Pointing.thickness(40, -1)).isZero();
        }
    }

    @Nested
    @DisplayName("how thick the ring is")
    class HowThick {

        @Test
        @DisplayName("is measured off the card, so a zoomed-out board is not ringed in smudges")
        void scalesWithTheCard() {
            assertThat(Pointing.thickness(120, 0)).isGreaterThan(Pointing.thickness(30, 0));
        }

        @Test
        @DisplayName("is never thinner than two, whatever the zoom")
        void neverInvisibleWhileShowing() {
            // A ring rounded down to a single pixel on a twelve-pixel card is a slightly
            // different shade of edge - the feature quietly not existing at exactly the zoom
            // level where finding one card among forty is hardest. Measured off a real
            // screenshot: the board draws cards twelve pixels across when zoomed out.
            for (int side = 4; side < 200; side += 3) {
                assertThat(Pointing.thickness(side, 0)).isGreaterThanOrEqualTo(2);
            }
        }
    }

    @Nested
    @DisplayName("the ring's color")
    class TheColor {

        @Test
        @DisplayName("keeps the hue it was given and moves only the alpha")
        void onlyAlphaMoves() {
            // A ring that changed color would be saying two things, and it has one to say.
            int ringed = Pointing.color(0xFFCC66, 0);
            assertThat(ringed & 0x00FFFFFF).isEqualTo(0xFFCC66);
            assertThat((ringed >>> 24) & 0xFF).isGreaterThan(200);
        }
    }
}
