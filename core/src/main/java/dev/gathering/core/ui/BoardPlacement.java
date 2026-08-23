package dev.gathering.core.ui;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;

/**
 * Where everything on the table is, in whatever space the answer is wanted in.
 *
 * <p>There are two ways to look at this board and they differ in exactly one thing: what a
 * point means. On the seated screen a point is a pixel, and a camera decides which part of the
 * felt is under it. Playing on the block, a point is a place on the shared surface, and the
 * game's own camera has already put it under the cursor by the time anything here is asked.
 *
 * <p>Everything else - which mat a drop lands on, which card is in front, where a pile sits,
 * how big a card is - is the same question in both, so it is asked through one interface and
 * answered twice. That is what stops the two views disagreeing about the board they are both
 * showing, and it is why the whole of the screen's hit-testing works unchanged in either.
 *
 * <p>Rectangles are integers in both spaces. Pixels obviously are; surface units are tenths of
 * a millimetre on a two-block table, so rounding to one costs nothing and buys the same
 * rotation-aware hit test both views need.
 */
public interface BoardPlacement {

    /**
     * Where a card sitting at this position on this seat's mat is drawn.
     *
     * <p>A position is the card's <b>middle</b>, not its corner. Corners look simpler and are
     * worse at the one place it matters: a card dropped near an edge could only ever hang off
     * two of the four sides, so half the border of every mat quietly shoved cards inwards
     * while the other half let them go. Measured from the middle, a card hangs off any edge
     * by the same amount, which is what a card on a real table does.
     */
    Rect rectOf(SeatId seat, TablePosition position);

    int cardWidth(SeatId seat);

    int cardHeight(SeatId seat);

    /**
     * How far round a card lying on this seat's mat is drawn, from the viewer's own chair.
     *
     * <p>A card faces its owner, so from the chair opposite it is upside down - which is what
     * a card on a table between two people does. The two views arrive at that differently: the
     * board in the world is looked at by a camera that faces the other way for half the
     * players, so the turn happens to the whole world at once, while the seated screen turns
     * its coordinates and has to turn each card itself. Asking the board rather than the
     * surface is what keeps the answer the same on both.
     */
    int facingDegrees(SeatId seat);

    /** The position a card's corner would have if dropped at this point. */
    TablePosition positionOn(SeatId seat, double x, double y);

    /** Whose mat is under this point, or null for the felt between them. */
    SeatId seatAt(double x, double y);

    Rect matRect(SeatId seat);

    Rect pileRect(SeatId seat, int index, int count);

    /**
     * The box drawn round a run of the column, from one zone to another inclusive.
     *
     * <p>Two of them on every mat: one round the zones a hand lives in and one round the
     * command zone, standing on its own.
     */
    Rect pileGroupRect(SeatId seat, int fromIndex, int toIndex, int count);

    /** Which of a seat's piles a point is on, or -1 - so a card can be dropped into one. */
    int pileAt(SeatId seat, int count, double x, double y);

    /** The table this is a placement for, so callers can ask it how many seats there are. */
    TableSurface surface();
}
