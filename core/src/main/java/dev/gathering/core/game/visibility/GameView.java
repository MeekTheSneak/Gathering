package dev.gathering.core.game.visibility;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TurnMarker;
import java.util.List;
import java.util.Map;

/**
 * The whole table as one viewer is entitled to see it.
 *
 * <p>This is what goes on the wire. It is derived from the authoritative state by
 * {@link VisibilityRules} and is the only thing a client ever receives about a session, which
 * is what makes the security property checkable: assert on this object and you have asserted
 * on the payload.
 */
public record GameView(Viewer viewer, List<SeatView> seats, TurnMarker turn, boolean ended) {

    public GameView {
        seats = List.copyOf(seats);
    }

    public SeatView seat(SeatId id) {
        return seats.stream()
                .filter(view -> view.seat().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such seat in this view: " + id));
    }

    /** Every card view the viewer received, flattened. The thing the invariant asserts over. */
    public List<CardView> allCardViews() {
        return seats.stream()
                .flatMap(seat -> seat.zones().values().stream())
                .flatMap(zone -> zone.cards().stream())
                .toList();
    }

    public Map<SeatId, Integer> lifeTotals() {
        return seats.stream().collect(
                java.util.stream.Collectors.toMap(SeatView::seat, SeatView::life, (a, b) -> a, java.util.LinkedHashMap::new));
    }
}
