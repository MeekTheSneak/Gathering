package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One turn of the wheel over a list, answered the same way everywhere. */
class ListScrollTest {

    @Test
    @DisplayName("moves one row per notch")
    void oneRowPerNotch() {
        assertThat(ListScroll.after(5, 40, 10, -1.0)).isEqualTo(6);
        assertThat(ListScroll.after(5, 40, 10, 1.0)).isEqualTo(4);
    }

    @Test
    @DisplayName("moves one row however hard the wheel was turned")
    void aFastWheelStillMovesOne() {
        // A wheel that jumped ten rows would skip past the one row somebody was aiming for.
        assertThat(ListScroll.after(5, 40, 10, -9.5)).isEqualTo(6);
    }

    @Test
    @DisplayName("stops at the top")
    void stopsAtTheTop() {
        assertThat(ListScroll.after(0, 40, 10, 1.0)).isZero();
    }

    @Test
    @DisplayName("stops with the last row at the bottom of the window, not past it")
    void stopsAtTheBottom() {
        // Forty rows, ten showing: the deepest first row is thirty, which puts the last row at
        // the bottom. Thirty-one would be a window with a gap where a row should be.
        assertThat(ListScroll.after(30, 40, 10, -1.0)).isEqualTo(30);
        assertThat(ListScroll.after(29, 40, 10, -1.0)).isEqualTo(30);
    }

    @Test
    @DisplayName("does not scroll a list that already fits")
    void aShortListDoesNotMove() {
        assertThat(ListScroll.scrolls(5, 10)).isFalse();
        assertThat(ListScroll.after(0, 5, 10, -1.0)).isZero();
    }

    @Test
    @DisplayName("says a long list scrolls")
    void aLongListScrolls() {
        assertThat(ListScroll.scrolls(40, 10)).isTrue();
    }

    @Test
    @DisplayName("ignores a wheel that did not move")
    void aStillWheel() {
        assertThat(ListScroll.after(5, 40, 10, 0.0)).isEqualTo(5);
    }
}
