package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The strip of seats along the top of the board, for two players to eight. */
class SeatStripTest {

    @Test
    @DisplayName("two players are always one row; eight in a small window go into two rows of four")
    void rows() {
        assertThat(SeatStrip.rowsFor(460, 2, 150, 90)).isEqualTo(1);
        assertThat(SeatStrip.rowsFor(1800, 8, 260, 90)).isEqualTo(1);
        assertThat(SeatStrip.rowsFor(460, 8, 150, 90)).isEqualTo(2);
        assertThat(SeatStrip.rowsFor(460, 4, 150, 90)).isEqualTo(2);
        assertThat(SeatStrip.rowsFor(900, 4, 260, 90)).isEqualTo(1);
    }

    /**
     * The board's own measures at the default text size: 127 a seat for a face, a name and a life total, and
     * 260 for the turn. In an interface 854 wide - GUI scale 1 on a display with one pixel to a point - the
     * strip has 824 inside and eight go into two rows; one row starts at an interface of 1306, 1276 inside.
     */
    @Test
    @DisplayName("at the board's own measures, eight take one row from an interface 1306 wide, not 854")
    void eightAtTheBoardsOwnMeasures() {
        assertThat(SeatStrip.rowsFor(824, 8, 260, 127)).isEqualTo(2);
        assertThat(SeatStrip.rowsFor(1275, 8, 260, 127)).isEqualTo(2);
        assertThat(SeatStrip.rowsFor(1276, 8, 260, 127)).isEqualTo(1);
        assertThat(SeatStrip.rowsFor(1678, 8, 260, 127)).isEqualTo(1);
    }

    @Test
    @DisplayName("every seat gets a column inside the strip, clear of the turn and of each other")
    void columns() {
        for (int seats = 1; seats <= 8; seats++) {
            for (int rows = 1; rows <= 2; rows++) {
                Rect status = new Rect(0, 0, 480, SeatStrip.ROW * rows);
                SeatStrip strip = SeatStrip.of(status, rows, seats, 6, 454, 150, 8);
                assertThat(strip.seats()).hasSize(seats);
                for (int one = 0; one < seats; one++) {
                    Rect seat = strip.seats().get(one);
                    assertThat(seat.bottom()).isLessThanOrEqualTo(status.bottom());
                    assertThat(seat.right()).isLessThanOrEqualTo(strip.turn().x());
                    for (int two = one + 1; two < seats; two++) {
                        Rect other = strip.seats().get(two);
                        boolean overlap = seat.x() < other.right() && other.x() < seat.right()
                                && seat.y() < other.bottom() && other.y() < seat.bottom();
                        assertThat(overlap).as("seats %d and %d of %d in %d rows", one, two, seats, rows).isFalse();
                    }
                }
                assertThat(strip.terms().isEmpty()).isEqualTo(rows == 1);
            }
        }
    }

    @Test
    @DisplayName("two rows give each of eight seats twice the room one row does")
    void twoRowsDoubleTheRoom() {
        int one = SeatStrip.of(new Rect(0, 0, 480, 16), 1, 8, 6, 454, 150, 8).seats().get(0).width();
        int two = SeatStrip.of(new Rect(0, 0, 480, 32), 2, 8, 6, 454, 150, 8).seats().get(0).width();
        assertThat(two).isGreaterThanOrEqualTo(one * 2);
    }

    @Test
    @DisplayName("a point finds the seat under it")
    void seatAt() {
        SeatStrip strip = SeatStrip.of(new Rect(0, 0, 480, 32), 2, 8, 6, 454, 150, 8);
        Rect fifth = strip.seats().get(4);
        assertThat(strip.seatAt(fifth.x() + 1, fifth.y() + 1)).isEqualTo(4);
        assertThat(strip.seatAt(strip.turn().x() + 1, 1)).isEqualTo(-1);
    }
}
