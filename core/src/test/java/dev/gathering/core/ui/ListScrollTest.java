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

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a scrollbar's thumb is the share showing, and runs from the top of the track to the bottom")
    void theThumb() {
        org.assertj.core.api.Assertions.assertThat(ListScroll.thumb(10, 100, 0, 10, 40)).containsExactly(10, 25);
        org.assertj.core.api.Assertions.assertThat(ListScroll.thumb(10, 100, 30, 10, 40)).containsExactly(85, 25);
        // Nothing hidden: the thumb is the track.
        org.assertj.core.api.Assertions.assertThat(ListScroll.thumb(10, 100, 0, 10, 5)).containsExactly(10, 100);
        // A long list keeps a thumb to see.
        org.assertj.core.api.Assertions.assertThat(ListScroll.thumb(0, 100, 0, 5, 5000)[1]).isEqualTo(ListScroll.SHORTEST_THUMB);
    }

    /**
     * A window that grew must not leave the scroll past the end of the list.
     * <p>How many rows fit is worked out from the window, so growing it - or dropping the GUI scale
     * - raises the count while the scroll stays where the player left it. Three screens do this and
     * only two clamped; the one that did not read past the end of its list inside layout, which
     * throws out of a screen's init and takes the client down rather than being caught anywhere.
     */
    @Test
    @DisplayName("a window that grew does not leave the scroll past the end")
    void awindowThatGrewClampsTheScroll() {
        // Eight rows, five fitting, scrolled to the deepest it can be.
        assertThat(ListScroll.within(3, 8, 5)).isEqualTo(3);
        // The window grows and all eight fit: there is nothing left to scroll past.
        assertThat(ListScroll.within(3, 8, 8)).isZero();
        // And somewhere in between it settles on the new deepest.
        assertThat(ListScroll.within(3, 8, 7)).isEqualTo(1);
    }

    @Test
    @DisplayName("nothing to show is scrolled to nought rather than to a negative")
    void anemptyListScrollsToNought() {
        assertThat(ListScroll.within(4, 0, 5)).isZero();
        assertThat(ListScroll.within(0, 0, 0)).isZero();
        assertThat(ListScroll.within(-2, 8, 3)).isZero();
        // A window taller than the list: every row is showing, so the first one is showing.
        assertThat(ListScroll.within(7, 3, 99)).isZero();
    }

    /** A page past the end of a list that shrank comes back to the last page it has. */
    @org.junit.jupiter.api.Test
    void aPageIsKeptToAPageTheListHas() {
        org.assertj.core.api.Assertions.assertThat(ListScroll.pageWithin(3, 12, 6)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(ListScroll.pageWithin(3, 0, 6)).isZero();
        org.assertj.core.api.Assertions.assertThat(ListScroll.pageWithin(-2, 12, 6)).isZero();
        org.assertj.core.api.Assertions.assertThat(ListScroll.pageWithin(1, 13, 6)).isEqualTo(1);
    }
}
