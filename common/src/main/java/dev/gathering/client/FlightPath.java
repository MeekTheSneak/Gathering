package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.ui.BoardPlacement;
import dev.gathering.core.ui.CardTravel;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.Travelling;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Where a card in the air is, in whichever space the board being drawn is measured in.
 *
 * <p>The two views disagree about exactly one thing - what a point means, pixels on a window
 * or a place on the felt - and a {@link BoardPlacement} is the thing that knows which. So a
 * flight is kept as the two places it goes between and turned into a rectangle here, once per
 * view per frame, rather than as a pair of rectangles that would be right in one view and
 * nonsense in the other.
 *
 * <p>Client-only.
 */
final class FlightPath {

    private FlightPath() {
    }

    /** Where this flight has got to, or nothing when neither end can be placed. */
    static Rect at(
            BoardPlacement board, BlockPos table, int pileCount,
            ClientCardFlights.Flight flight, long now) {
        Optional<CardInstanceId> card = flight.move().card();
        Rect from = rectOf(board, table, pileCount, flight.move().from(), card);
        Rect to = rectOf(board, table, pileCount, flight.move().to(), card);
        if (from.isEmpty() && to.isEmpty()) {
            return Rect.NONE;
        }
        return Travelling.between(from, to, flight.progress(now));
    }

    /**
     * Where a place is on this board.
     *
     * <p>A pile has a slot. A hand has an edge rather than a slot, because a hand is not on
     * the table. The battlefield has wherever the card actually was: remembered, because a
     * card that has just left it is not in the board any more, and the middle of a mat is not
     * where anybody watched it sitting.
     */
    private static Rect rectOf(
            BoardPlacement board, BlockPos table, int pileCount,
            CardTravel.Place place, Optional<CardInstanceId> card) {
        if (place.zone() == Zone.HAND) {
            return board.handEdgeRect(place.seat());
        }
        if (place.zone() == Zone.BATTLEFIELD) {
            TablePosition sat = card
                    .flatMap(id -> ClientCardFlights.lastSatAt(table, id))
                    .orElse(null);
            return sat == null ? middleOf(board, place) : board.rectOf(place.seat(), sat);
        }
        int index = Zone.PILES.indexOf(place.zone());
        return index < 0 ? Rect.NONE : board.pileRect(place.seat(), index, pileCount);
    }

    /** A card-sized rectangle in the middle of a mat, for a card nobody can place exactly. */
    private static Rect middleOf(BoardPlacement board, CardTravel.Place place) {
        Rect mat = board.matRect(place.seat());
        if (mat.isEmpty()) {
            return Rect.NONE;
        }
        return board.rectOf(place.seat(), TablePosition.fraction(0.5, 0.5));
    }
}
