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

    /** Where a card sitting at this position on this seat's mat is drawn. */
    Rect rectOf(SeatId seat, TablePosition position);

    int cardWidth(SeatId seat);

    int cardHeight(SeatId seat);

    /** The position a card's corner would have if dropped at this point. */
    TablePosition positionOn(SeatId seat, double x, double y);

    /** Whose mat is under this point, or null for the felt between them. */
    SeatId seatAt(double x, double y);

    Rect matRect(SeatId seat);

    Rect pileRect(SeatId seat, int index, int count);

    /** The table this is a placement for, so callers can ask it how many seats there are. */
    TableSurface surface();
}
