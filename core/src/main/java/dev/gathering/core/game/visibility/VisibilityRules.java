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
 * which are events, which the log announces, and which open that library to exactly one seat
 * until something closes it again. So the one exception to the row above is stated by the
 * board itself - {@link GameState#openCardsOf} - rather than by whichever screen happens to
 * be showing a library at the time.
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
        return viewFor(state, viewer, List.of());
    }

    /**
     * A view with the public log attached.
     *
     * <p>The same log for every viewer, which is the point of it: the log is what everybody
     * agrees happened. It is safe to send unfiltered because {@link CardRef} has already
     * decided how strongly each line may name a card, against the board at the time - a line
     * about a card in somebody's hand says "a card" to its own author too.
     */
    public static GameView viewFor(
            GameState state, Viewer viewer, List<dev.gathering.core.game.event.LogEntry> log) {
        List<SeatView> seats = new ArrayList<>(state.seats().size());
        for (SeatId seat : state.seats()) {
            seats.add(seatView(state, seat, viewer));
        }
        return new GameView(viewer, seats, state.turn(), state.ended(), log);
    }

    /**
     * Every seated view plus the spectator view, for tests and for broadcast.
     *
     * <p><b>Never a historian.</b> This is what the invariant suites walk, and what a live
     * table hands out; a viewer entitled to hidden information has no business in either. The
     * omission is the enforcement - see {@link Viewer.Historian}.
     */
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
                seatState.lastOccupant(),
                seatState.life(),
                seatState.commanderDamage(),
                seatState.commanderTax(),
                seatState.commanders(),
                seatState.counters(),
                seatState.conceded(),
                seatState.handShownTo(),
                zones);
    }

    private static ZoneView zoneView(GameState state, ZoneRef ref, Viewer viewer) {
        List<CardInstanceId> contents = state.contents(ref);

        // A game that is over, read by somebody entitled to all of it. Every rule below is
        // about protecting information that is still live; none of it is, so none of them
        // apply. See Viewer.Historian for why this is the one safe exception and what keeps
        // it from becoming a live one.
        if (viewer.seesEverything()) {
            List<CardView> everything = new ArrayList<>(contents.size());
            for (CardInstanceId id : contents) {
                everything.add(cardView(state, state.requireCard(id), viewer));
            }
            return new ZoneView(ref, contents.size(), everything);
        }

        // A library is a count to everybody, its owner included - unless the log says this
        // viewer is looking through it right now, in which case they see exactly as far down
        // it as the event that opened it said they could.
        if (ref.zone() == Zone.LIBRARY) {
            // A spectator sees what the whole table sees and nothing more: a revealed card is
            // revealed to the room, not to a list of people.
            int open = viewer instanceof Viewer.Seated seated
                    ? state.openCardsOf(seated.seat(), ref.seat())
                    : state.revealedIn(ref.seat());
            if (open <= 0) {
                return ZoneView.countOnly(ref, contents.size());
            }
            List<CardView> top = new ArrayList<>(open);
            for (CardInstanceId id : contents.subList(0, Math.min(open, contents.size()))) {
                top.add(cardView(state, state.requireCard(id), viewer));
            }
            return new ZoneView(ref, contents.size(), top);
        }

        // A hand is full to its own seat and a count to everyone else - unless its owner has
        // turned it toward somebody, which is a thing they do on purpose and take back on
        // purpose. Spectators stay in "everyone else" even then: showing your hand is
        // something you do to the players you are playing against, and the mod's one security
        // property is worth more than a watcher's convenience. The log says it happened, so
        // nobody watching is left wondering why a hand changed hands.
        if (ref.zone() == Zone.HAND && !viewer.isSeatedAt(ref.seat())
                && !shownTo(state, ref.seat(), viewer)) {
            return ZoneView.countOnly(ref, contents.size());
        }

        List<CardView> cards = new ArrayList<>(contents.size());
        for (CardInstanceId id : contents) {
            cards.add(cardView(state, state.requireCard(id), viewer));
        }
        return new ZoneView(ref, contents.size(), cards);
    }

    /** Whether this hand's owner has turned it toward this viewer. */
    private static boolean shownTo(GameState state, SeatId owner, Viewer viewer) {
        return viewer instanceof Viewer.Seated seated
                && state.seatState(owner).handIsShownTo(seated.seat());
    }

    private static CardView cardView(GameState state, CardInstance card, Viewer viewer) {
        boolean entitled = !card.isFaceDown() || viewer.isSeatedAt(card.owner())
                || viewer.seesEverything();
        // The host this card sits on, named only to viewers whose world holds that id. A
        // face-down host is anonymous to everyone but its owner - its view carries a marker
        // and no id - and an instance id is a decklist position, so writing the real id into
        // an opponent's attachedTo told them which card of the deck the morph is. The log
        // already routes this through the marker; the view does the same by saying nothing.
        CardInstanceId host = card.attachedTo();
        if (host != null) {
            CardInstance hostCard = state.card(host).orElse(null);
            if (hostCard != null && hostCard.isFaceDown() && !viewer.isSeatedAt(hostCard.owner())
                    && !viewer.seesEverything()) {
                host = null;
            }
        }
        if (entitled) {
            return new CardView.Visible(
                    card.id(), card.identity(), card.owner(), card.facing(), card.tapped(),
                    card.counters(), card.position(), card.token(), host,
                    card.note(), card.turnedOver(), card.strength(), card.frozen());
        }
        // The spot goes to everyone: where a card sits was never a secret, and an opponent
        // who cannot see which card it is still has to be able to see that it is there.
        return new CardView.Anonymous(
                card.markerId().orElseThrow(() ->
                        new IllegalStateException("A face-down card without a marker: " + card.id())),
                card.tapped(),
                card.counters(),
                card.position(),
                host,
                // A note is what somebody wrote about the card, not what the card is. The
                // person who picked up the pen decided what it gave away.
                card.note(),
                // And the same for the numbers written in the corner: typed by a player,
                // never worked out from a card nobody else may name.
                card.strength(),
                // Freezing is something done to an opponent, and its whole value is that
                // they can see it. It says nothing about what the card is.
                card.frozen());
    }
}
