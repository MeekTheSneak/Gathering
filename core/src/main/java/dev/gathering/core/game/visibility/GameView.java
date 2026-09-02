package dev.gathering.core.game.visibility;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TurnMarker;
import java.util.List;
import java.util.Map;

/**
 * The whole table as one viewer is entitled to see it.
 * <p>This is what goes on the wire. It is derived from the authoritative state by
 * {@link VisibilityRules} and is the only thing a client ever receives about a session, which
 * is what makes the security property checkable: assert on this object and you have asserted
 * on the payload.
 */
public record GameView(
        Viewer viewer,
        List<SeatView> seats,
        TurnMarker turn,
        boolean ended,
        List<dev.gathering.core.game.event.LogEntry> log) {

    public GameView {
        seats = List.copyOf(seats);
        log = log == null ? List.of() : List.copyOf(log);
    }

    public SeatView seat(SeatId id) {
        return seats.stream()
                .filter(view -> view.seat().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such seat in this view: " + id));
    }

    /**
     * The next seat round the table that is somebody's, for handing the turn on.
     * <p>A cluster makes a seat for every place at it whether or not anybody is in one, so
     * four people at an eight-seat table handed the turn to four empty chairs between every
     * real turn - and the marker over the board spent most of the game naming nobody.
     * Skipping a chair is not a rules judgment: there is no player there to take a turn.
     * <p>A board rather than an occupant, so somebody who stood up mid-game still gets their
     * turn - their cards are on the table and the table is playing around them. Returns the
     * seat it was given when no other seat has a board, which is a solo game, and when the
     * seat is not part of this view at all.
     */
    public SeatId nextSeatWithABoard(SeatId from) {
        int at = -1;
        for (int index = 0; index < seats.size(); index++) {
            if (seats.get(index).seat().equals(from)) {
                at = index;
                break;
            }
        }
        if (at < 0) {
            return from;
        }
        for (int step = 1; step <= seats.size(); step++) {
            SeatView next = seats.get((at + step) % seats.size());
            if (next.hasABoard()) {
                return next.seat();
            }
        }
        return from;
    }

    /** Every card view the viewer received, flattened. The thing the invariant asserts over. */
    public List<CardView> allCardViews() {
        return seats.stream()
                .flatMap(seat -> seat.zones().values().stream())
                .flatMap(zone -> zone.cards().stream())
                .toList();
    }

}
