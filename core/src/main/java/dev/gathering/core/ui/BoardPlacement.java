package dev.gathering.core.ui;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything on the table is, in whatever space the answer is wanted in.
 * <p>There are two ways to look at this board and they differ in exactly one thing: what a
 * point means. On the seated screen a point is a pixel, and a camera decides which part of the
 * felt is under it. Playing on the block, a point is a place on the shared surface, and the
 * game's own camera has already put it under the cursor by the time anything here is asked.
 * <p>Everything else - which mat a drop lands on, which card is in front, where a pile sits,
 * how big a card is - is the same question in both, so it is asked through one interface and
 * answered twice. That is what stops the two views disagreeing about the board they are both
 * showing, and it is why the whole of the screen's hit-testing works unchanged in either.
 * <p>Rectangles are integers in both spaces. Pixels obviously are; surface units are tenths of
 * a millimeter on a two-block table, so rounding to one costs nothing and buys the same
 * rotation-aware hit test both views need.
 */
public interface BoardPlacement {

    /**
     * Where a card sitting at this position on this seat's mat is drawn.
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
     * <p>Two of them on every mat: one round the zones a hand lives in and one round the
     * command zone, standing on its own.
     */
    Rect pileGroupRect(SeatId seat, int fromIndex, int toIndex, int count);

    /** One of the verb buttons printed on a seat's own mat. */
    Rect verbRect(SeatId seat, int index, int count);

    /** The panel round the whole run of verb buttons. */
    Rect verbGroupRect(SeatId seat, int count);

    /** Where a zone's name is written on the felt beside its slot, or nothing if it will not fit. */
    Rect pileLabelRect(SeatId seat, int index, int count);

    /**
     * Where this seat's life total is written, on the table past the far edge of its mat.
     * <p>Empty when the table has no room for it. Both views ask: both draw the number and
     * both let a player press its halves.
     */
    Rect lifeRect(SeatId seat);

    /**
     * Where this seat's counters are written, past its life total, or empty for no room.
     * <p>On the table with the life total rather than in the strip along the top: see
     * {@link TableSurface#countersBox}.
     */
    Rect countersRect(SeatId seat);


    /** The line across a mat marking off the row nearest its own player. */
    Rect matDividerRect(SeatId seat, int count);

    /**
     * Where a card going into or out of this seat's hand is: the fan held at the near edge of
     * its mat.
     * <p>A hand has no slot on the table, so a card on its way to one needs somewhere to be
     * going. See {@link TableSurface#handEdge(int)}.
     */
    Rect handEdgeRect(SeatId seat);

    /** Which of a seat's piles a point is on, or -1 - so a card can be dropped into one. */
    int pileAt(SeatId seat, int count, double x, double y);

    /** The table this is a placement for, so callers can ask it how many seats there are. */
    TableSurface surface();

    /**
     * A rectangle in the table's own coordinates, as one on the screen.
     * <p>The two boards differ in exactly this: one draws the surface at its own size and the
     * other looks at it through a camera. Anything laid out on the surface - the pot, and
     * whatever comes after it - goes through here rather than each board growing its own
     * accessor for it, which is how the two ended up disagreeing about card sizes before.
     */
    Rect fromSurface(Rect onSurface);

    /**
     * One other seat's hand, as this board draws it on the felt.
     *
     * @param seat  whose hand it is
     * @param cards each card as drawn, the one on top last
     * @param faces what the view carries for those cards, by the same index: a hand shown to this
     *     viewer, or any hand in a replay. Empty for a hand drawn as its sleeve's backs.
     */
    record HandOnTheFelt(SeatView seat, List<HandCard> cards, List<CardView> faces) {
    }

    /**
     * One card of a hand on the felt, as a board draws it.
     *
     * @param where its rectangle in this board's space before it is turned; it turns about its middle
     * @param angle degrees clockwise, from this board's own upright - the seat's facing, or the
     *     viewer's for a row of faces, already added, exactly as for a card lying on a mat
     */
    record HandCard(Rect where, int angle) {
    }

    /**
     * Every hand drawn on the felt of this board, and exactly how: asked by both boards' painters
     * and by the screen's picker, so the two boards cannot draw a hand two ways and the picker
     * cannot miss a card the painter drew.
     * <p>Every seat with a board and cards in hand, except the viewer's own, which is along the
     * bottom of the window face up. A hand the view carries faces for is laid in a row and turned to
     * the viewer, the way a hand turned toward somebody is held; any other hand is the fan of the
     * seat's backs, turned with that seat, the way a card lying on its mat is. The layout is asked a
     * count and nothing else: which of the two it is was already decided by the view.
     * <p>Over everything lying on the table, on both boards: a hand is held above the table, not
     * laid under the cards on it. So nothing under one of these cards can be pointed at through it.
     */
    default List<HandOnTheFelt> handsOnTheFelt(GameView board) {
        SeatId viewer = board.viewer().seatId().orElse(null);
        // Upright to whoever is reading, on either board: the flat one turns its own coordinates so the
        // viewer's mat faces them, and the block's camera does the turning, so the viewer's own facing
        // is the right way up in both. Nobody seated - a replay - is the board as it lies.
        int toTheViewer = viewer == null ? 0 : facingDegrees(viewer);
        List<HandOnTheFelt> hands = new ArrayList<>();
        for (SeatView seat : board.seats()) {
            if (seat.seat().equals(viewer) || !seat.hasABoard()) {
                continue;
            }
            // Asked of the map, not of zone(): a seat still being set up can arrive without one,
            // and zone() throws for that.
            ZoneView hand = seat.zones().get(Zone.HAND);
            if (hand == null || hand.count() <= 0) {
                continue;
            }
            List<CardView> faces = hand.cards();
            int index = seat.seat().index();
            int facing = facingDegrees(seat.seat());
            List<FeltHand.Slot> laid = faces.isEmpty()
                    ? surface().handFan(index, hand.count())
                    : surface().handRow(index, hand.count());
            // A row runs to its player's right. Read from the chair opposite, that is backwards: each
            // card would cover the left of the one under it, where its name is. So a row read the other
            // way up from its seat is laid right to left along the same places.
            boolean readBackwards = !faces.isEmpty() && Math.floorMod(toTheViewer - facing, 360) != 0;
            int turn = faces.isEmpty() ? facing : toTheViewer;
            List<HandCard> drawn = new ArrayList<>(laid.size());
            for (int at = 0; at < laid.size(); at++) {
                FeltHand.Slot slot = laid.get(readBackwards ? laid.size() - 1 - at : at);
                drawn.add(new HandCard(fromSurface(slot.where()), slot.angle() + turn));
            }
            hands.add(new HandOnTheFelt(seat, List.copyOf(drawn), faces));
        }
        return List.copyOf(hands);
    }
}
