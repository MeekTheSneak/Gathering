package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A short mark for a seat that somebody who cannot see its color can still read. */
class SeatMarkTest {

    @Test
    @DisplayName("names the first seat the way a person sitting at it would")
    void oneBased() {
        assertThat(SeatMark.of(0)).isEqualTo("1");
        assertThat(SeatMark.of(3)).isEqualTo("4");
    }

    @Test
    @DisplayName("gives every seat in the palette its own mark")
    void everySeatIsToldApart() {
        java.util.Set<String> marks = new java.util.HashSet<>();
        for (int seat = 0; seat < SeatColor.count(); seat++) {
            marks.add(SeatMark.of(seat));
        }
        assertThat(marks).hasSize(SeatColor.count());
    }

    @Test
    @DisplayName("wraps rather than throwing, exactly as the colors do")
    void wrapsLikeTheColors() {
        // A cluster with more seats than the palette still has to name every one of them
        // rather than throw in the middle of drawing a board.
        assertThat(SeatMark.of(SeatColor.count())).isEqualTo(SeatMark.of(0));
        assertThat(SeatMark.of(-1)).isEqualTo(SeatMark.of(SeatColor.count() - 1));
    }
}
