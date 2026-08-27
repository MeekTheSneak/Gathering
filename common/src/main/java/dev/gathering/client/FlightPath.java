package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.ui.BoardPlacement;
import dev.gathering.core.ui.CardTravel;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.Traveling;
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
        Rect from = rectOf(board, pileCount, flight.move().from(), flight.move().fromSpot());
        Rect to = rectOf(board, pileCount, flight.move().to(), flight.move().toSpot());
        if (from.isEmpty() && to.isEmpty()) {
            return Rect.NONE;
        }
        return Traveling.between(from, to, flight.progress(now));
    }

    /**
     * Where a place is on this board.
     *
     * <p>A pile has a slot. A hand has an edge rather than a slot, because a hand is not on
     * the table. The battlefield has wherever the card actually was, which the flight carries
     * with it - a card that has just left a mat is not in the board any more, and the middle
     * of a mat is not where anybody watched it sitting.
     */
    private static Rect rectOf(
            BoardPlacement board, int pileCount,
            CardTravel.Place place, Optional<TablePosition> spot) {
        if (place.zone() == Zone.HAND) {
            return board.handEdgeRect(place.seat());
        }
        if (place.zone() == Zone.BATTLEFIELD) {
            return spot.map(sat -> board.rectOf(place.seat(), sat))
                    .orElseGet(() -> middleOf(board, place));
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
