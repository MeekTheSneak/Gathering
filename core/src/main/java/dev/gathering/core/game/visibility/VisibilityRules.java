package dev.gathering.core.game.visibility;

import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the authoritative board into what one viewer is entitled to see.
 *
 * <p>This class is the mod's single security property, and everything about it is arranged
 * so that the property is structural rather than remembered:
 *
 * <table>
 *   <caption>Section 6's table, which this implements exactly</caption>
 *   <tr><th>Zone</th><th>Owner</th><th>Opponents</th><th>Spectators</th></tr>
 *   <tr><td>Library</td><td>count</td><td>count</td><td>count</td></tr>
 *   <tr><td>Hand</td><td>full</td><td>count</td><td>count</td></tr>
 *   <tr><td>Battlefield, face up</td><td>full</td><td>full</td><td>full</td></tr>
 *   <tr><td>Battlefield, face down</td><td>full</td><td>marker</td><td>marker</td></tr>
 *   <tr><td>Graveyard</td><td>full</td><td>full</td><td>full</td></tr>
 *   <tr><td>Exile, face up</td><td>full</td><td>full</td><td>full</td></tr>
 *   <tr><td>Exile, face down</td><td>full</td><td>marker</td><td>marker</td></tr>
 *   <tr><td>Command zone</td><td>full</td><td>full</td><td>full</td></tr>
 * </table>
 *
 * <p>Note that a library is count-only <em>even for its owner</em>. Knowing the order of
 * your own library is not a thing you are entitled to; looking at it is a scry or a search,
 * which are events, and which the log announces.
 *
 * <p>A face-down card is read by its <b>owner</b>, not by whoever currently controls it. That
 * is the conservative reading of the table above, and it is conservative on purpose: if a
 * group decides the controller ought to be able to read a stolen face-down permanent, they
 * can turn it face up, which is one click and shows in the log. The reverse mistake cannot
 * be undone.
 */
public final class VisibilityRules {

    private VisibilityRules() {
    }

    public static GameView viewFor(GameState state, Viewer viewer) {
        List<SeatView> seats = new ArrayList<>(state.seats().size());
        for (SeatId seat : state.seats()) {
            seats.add(seatView(state, seat, viewer));
        }
        return new GameView(viewer, seats, state.turn(), state.ended());
    }

    /** Every seated view plus the spectator view, for tests and for broadcast. */
    public static Map<Viewer, GameView> allViews(GameState state) {
        Map<Viewer, GameView> views = new java.util.LinkedHashMap<>();
        for (SeatId seat : state.seats()) {
            Viewer viewer = Viewer.seat(seat);
            views.put(viewer, viewFor(state, viewer));
        }
        views.put(Viewer.SPECTATOR, viewFor(state, Viewer.SPECTATOR));
        return views;
    }

    private static SeatView seatView(GameState state, SeatId seat, Viewer viewer) {
        SeatState seatState = state.seatState(seat);
        Map<Zone, ZoneView> zones = new EnumMap<>(Zone.class);
        for (Zone zone : Zone.values()) {
            zones.put(zone, zoneView(state, ZoneRef.of(seat, zone), viewer));
        }
        return new SeatView(
                seat,
                seatState.occupant(),
                seatState.life(),
                seatState.commanderDamage(),
                seatState.commanderTax(),
                seatState.conceded(),
                zones);
    }

    private static ZoneView zoneView(GameState state, ZoneRef ref, Viewer viewer) {
        List<CardInstanceId> contents = state.contents(ref);

        // A library is a count to everybody, its owner included.
        if (ref.zone() == Zone.LIBRARY) {
            return ZoneView.countOnly(ref, contents.size());
        }

        // A hand is full to its own seat and a count to everyone else. Spectators are
        // "everyone else" - that is the whole reason a spectating client cannot leak one.
        if (ref.zone() == Zone.HAND && !viewer.isSeatedAt(ref.seat())) {
            return ZoneView.countOnly(ref, contents.size());
        }

        List<CardView> cards = new ArrayList<>(contents.size());
        for (CardInstanceId id : contents) {
            cards.add(cardView(state.requireCard(id), viewer));
        }
        return new ZoneView(ref, contents.size(), cards);
    }

    private static CardView cardView(CardInstance card, Viewer viewer) {
        boolean entitled = !card.isFaceDown() || viewer.isSeatedAt(card.owner());
        if (entitled) {
            return new CardView.Visible(
                    card.id(), card.identity(), card.owner(), card.facing(), card.tapped(),
                    card.counters(), card.position(), card.token());
        }
        // The spot goes to everyone: where a card sits was never a secret, and an opponent
        // who cannot see which card it is still has to be able to see that it is there.
        return new CardView.Anonymous(
                card.markerId().orElseThrow(() ->
                        new IllegalStateException("A face-down card without a marker: " + card.id())),
                card.tapped(),
                card.counters(),
                card.position());
    }
}
