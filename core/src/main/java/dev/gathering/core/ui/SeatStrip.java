package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each seat's column goes in the strip along the top of the board, and where the turn goes.
 * <p>One row of equal columns reads well for two or four players. At six or eight in an ordinary
 * window a column is thirty-odd pixels, which holds a seat's number and its life total and nothing
 * else: who is sitting there, how many cards they hold and how much library they have left all
 * went. So when a single row would leave a seat less room than its face, its name and its life
 * need, the seats go into two rows - four to a row for eight players - and the strip is twice as
 * tall. The turn keeps the right-hand end, with what the table is playing under it on the second row.
 * <p>Pure, so the board, its tests and the strip's hit-testing for tooltips ask the same rectangles.
 */
public record SeatStrip(int rows, List<Rect> seats, Rect turn, Rect terms) {

    /** How tall one row of the strip is. The writing's own height and a little air. */
    public static final int ROW = 16;

    /** The most rows the strip is given: past two it is a panel, not a strip. */
    public static final int MOST_ROWS = 2;

    public SeatStrip {
        seats = List.copyOf(seats);
    }

    /**
     * How many rows a strip this wide wants for this many seats.
     *
     * @param inside    the strip's width less its padding and anything at its end
     * @param turnWidth how much of it the turn keeps
     * @param leastSeat the least a seat's column needs for its face, name and life
     */
    public static int rowsFor(int inside, int seatCount, int turnWidth, int leastSeat) {
        if (seatCount <= 2) {
            return 1;
        }
        int oneRow = Math.max(0, inside - turnWidth) / seatCount;
        return oneRow < leastSeat ? MOST_ROWS : 1;
    }

    /**
     * The strip laid out in a status area already sized for its rows.
     *
     * @param left      where the first column starts
     * @param inside    how wide the columns and the turn may be, together
     * @param turnWidth how much of that the turn keeps, at the right-hand end
     * @param gap       the space kept between one column and the next
     */
    public static SeatStrip of(Rect status, int rows, int seatCount, int left, int inside, int turnWidth, int gap) {
        int usedRows = Math.max(1, Math.min(MOST_ROWS, rows));
        int rowHeight = Math.max(1, status.height() / usedRows);
        int perRow = Math.max(1, (seatCount + usedRows - 1) / usedRows);
        int column = Math.max(1, (inside - turnWidth) / perRow);
        List<Rect> seats = new ArrayList<>();
        for (int index = 0; index < seatCount; index++) {
            seats.add(new Rect(left + index % perRow * column, status.y() + index / perRow * rowHeight,
                    Math.max(1, column - gap), rowHeight));
        }
        int turnX = left + inside - turnWidth;
        Rect turn = new Rect(turnX, status.y(), turnWidth, rowHeight);
        Rect terms = usedRows > 1 ? new Rect(turnX, status.y() + rowHeight, turnWidth, rowHeight) : Rect.NONE;
        return new SeatStrip(usedRows, seats, turn, terms);
    }

    /** Which seat's column a point is over, or -1. */
    public int seatAt(int x, int y) {
        for (int index = 0; index < seats.size(); index++) {
            if (seats.get(index).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }
}
