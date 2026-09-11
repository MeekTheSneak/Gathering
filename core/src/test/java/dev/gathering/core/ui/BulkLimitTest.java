package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** How much one gesture is allowed to do. */
class BulkLimitTest {

    private static List<Integer> many(int how) {
        List<Integer> made = new ArrayList<>();
        for (int index = 0; index < how; index++) {
            made.add(index);
        }
        return made;
    }

    @Nested
    @DisplayName("an ordinary gesture")
    class AnOrdinaryGesture {

        @Test
        @DisplayName("is not clipped, and says so")
        void ordinaryPassesThrough() {
            BulkLimit.Batch<Integer> batch = BulkLimit.take(many(40));
            assertThat(batch.size()).isEqualTo(40);
            assertThat(batch.askedFor()).isEqualTo(40);
            assertThat(batch.wasClipped()).isFalse();
            assertThat(batch.leftOut()).isZero();
        }

        @Test
        @DisplayName("keeps the order it was given")
        void orderIsKept() {
            // The order a selection is in is the order the cards were picked up in. Taking a
            // sample instead would make the same gesture do different things twice.
            assertThat(BulkLimit.take(List.of(7, 3, 9)).doing()).containsExactly(7, 3, 9);
        }

        @Test
        @DisplayName("at exactly the bound is still not clipped")
        void theBoundItselfFits() {
            BulkLimit.Batch<Integer> batch = BulkLimit.take(many(BulkLimit.MOST_AT_ONCE));
            assertThat(batch.size()).isEqualTo(BulkLimit.MOST_AT_ONCE);
            assertThat(batch.wasClipped()).isFalse();
        }
    }

    @Nested
    @DisplayName("a gesture past the bound")
    class PastTheBound {

        @Test
        @DisplayName("does the bound's worth and reports the rest")
        void clipsAndSaysHowMuch() {
            int asked = BulkLimit.MOST_AT_ONCE + 37;
            BulkLimit.Batch<Integer> batch = BulkLimit.take(many(asked));
            assertThat(batch.size()).isEqualTo(BulkLimit.MOST_AT_ONCE);
            assertThat(batch.askedFor()).isEqualTo(asked);
            assertThat(batch.wasClipped()).isTrue();
            assertThat(batch.leftOut()).isEqualTo(37);
        }

        @Test
        @DisplayName("takes the front of the list, not a sample")
        void takesTheFront() {
            List<Integer> asked = many(BulkLimit.MOST_AT_ONCE + 5);
            assertThat(BulkLimit.take(asked).doing())
                    .isEqualTo(asked.subList(0, BulkLimit.MOST_AT_ONCE));
        }

        @Test
        @DisplayName("cannot be made unbounded by asking for more")
        void theBoundHolds() {
            // The whole point. One gesture makes at most this much work however big the
            // selection behind it got.
            for (int asked : List.of(200, 1_000, 50_000)) {
                assertThat(BulkLimit.take(many(asked)).size()).isEqualTo(BulkLimit.MOST_AT_ONCE);
            }
        }
    }

    @Nested
    @DisplayName("a gesture with nothing in it")
    class NothingInIt {

        @Test
        @DisplayName("is empty rather than an exception")
        void emptyInEmptyOut() {
            assertThat(BulkLimit.take(List.of()).isEmpty()).isTrue();
            assertThat(BulkLimit.take(null).isEmpty()).isTrue();
            assertThat(BulkLimit.take(null).wasClipped()).isFalse();
        }
    }

    @Nested
    @DisplayName("the bound itself")
    class TheBoundItself {

        @Test
        @DisplayName("is above any real board")
        void aboveAnyRealBoard() {
            // Set on purpose above a crowded Commander table, because clipping a genuine
            // board wipe would be a worse bug than the unbounded case it prevents: the player
            // looks at the felt, sees cards still on it, and has no idea why.
            assertThat(BulkLimit.MOST_AT_ONCE).isGreaterThan(100);
        }
    }
}
