package dev.gathering.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The whole board, as an immutable value.
 *
 * <p>State is the fold of the event log and nothing else. Nothing mutates a {@code GameState};
 * every change produces a new one. That is what makes undo a re-fold rather than a parallel
 * bookkeeping system, and what makes replay fall out of the architecture instead of needing
 * to be built.
 *
 * <p>Copying the whole board on every event sounds expensive and is not: a four-player
 * Commander game is a few hundred cards and events arrive at human speed. A full re-fold of
 * a long game costs milliseconds, which is the price of never having an undo bug.
 *
 * @param cards          every card in the session, by instance
 * @param zones          ordered contents of each seat's each zone
 * @param peeks          who is currently looking through whose library; see {@link Peek}
 * @param revealed       how many cards off the top of each library are face up to everybody
 * @param nextCardId     the counter tokens and copies draw from
 * @param shuffleOrdinal how many shuffles have happened; the permutation for each is derived
 *                       from the session seed and this, so the log never stores an order
 * @param markerOrdinal  how many face-down markers have been handed out
 */
public record GameState(
        List<SeatId> seats,
        Map<CardInstanceId, CardInstance> cards,
        Map<ZoneRef, List<CardInstanceId>> zones,
        Map<SeatId, SeatState> seatStates,
        Map<SeatId, Peek> peeks,
        Map<SeatId, Integer> revealed,
        TurnMarker turn,
        int nextCardId,
        int shuffleOrdinal,
        int markerOrdinal,
        boolean ended) {

    public GameState {
        seats = List.copyOf(seats);
        cards = Collections.unmodifiableMap(new LinkedHashMap<>(cards));
        zones = deepImmutable(zones);
        seatStates = Collections.unmodifiableMap(new LinkedHashMap<>(seatStates));
        peeks = peeks == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(peeks));
        revealed = revealed == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(revealed));
    }

    /** An empty table with the given seats, before any card has entered. */
    public static GameState empty(List<SeatId> seats, int startingLife) {
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("A session needs at least one seat");
        }
        Map<ZoneRef, List<CardInstanceId>> zones = new LinkedHashMap<>();
        Map<SeatId, SeatState> seatStates = new LinkedHashMap<>();
        for (SeatId seat : seats) {
            for (Zone zone : Zone.values()) {
                zones.put(ZoneRef.of(seat, zone), List.of());
            }
            seatStates.put(seat, SeatState.startingAt(seat, startingLife));
        }
        return new GameState(
                seats, Map.of(), zones, seatStates, Map.of(), Map.of(),
                TurnMarker.start(seats.get(0)), 0, 0, 0, false);
    }

    // ---------------------------------------------------------------- queries

    public Optional<CardInstance> card(CardInstanceId id) {
        return Optional.ofNullable(cards.get(id));
    }

    public CardInstance requireCard(CardInstanceId id) {
        CardInstance card = cards.get(id);
        if (card == null) {
            throw new IllegalArgumentException("No such card in this session: " + id);
        }
        return card;
    }

    public List<CardInstanceId> contents(ZoneRef ref) {
        return zones.getOrDefault(ref, List.of());
    }

    public List<CardInstanceId> contents(SeatId seat, Zone zone) {
        return contents(ZoneRef.of(seat, zone));
    }

    public int count(ZoneRef ref) {
        return contents(ref).size();
    }

    /** Where a card currently is, or empty if it has left the session entirely. */
    public Optional<ZoneRef> locationOf(CardInstanceId id) {
        for (Map.Entry<ZoneRef, List<CardInstanceId>> entry : zones.entrySet()) {
            if (entry.getValue().contains(id)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** What this seat is currently looking through, if anything. */
    public Optional<Peek> peekBy(SeatId seat) {
        return Optional.ofNullable(peeks.get(seat));
    }

    /**
     * Whether {@code viewer} is currently entitled to see the top of {@code library}.
     *
     * <p>The single question the visibility rules ask about looking, phrased once here so
     * that adding a fourth way to look at a library cannot accidentally add a fourth answer.
     */
    public int openCardsOf(SeatId viewer, SeatId library) {
        int open = revealedIn(library);
        Peek peek = peeks.get(viewer);
        if (peek != null && peek.at().equals(library)) {
            open = Math.max(open, peek.visibleCount(count(ZoneRef.of(library, Zone.LIBRARY))));
        }
        return open;
    }

    /**
     * How many cards off the top of a library are face up to the whole table.
     *
     * <p>The one thing about a library that reaches a spectator, because a revealed card is
     * revealed to the room and not to a list of people.
     */
    public int revealedIn(SeatId library) {
        return Math.min(
                revealed.getOrDefault(library, 0), count(ZoneRef.of(library, Zone.LIBRARY)));
    }

    public SeatState seatState(SeatId seat) {
        SeatState state = seatStates.get(seat);
        if (state == null) {
            throw new IllegalArgumentException("No such seat in this session: " + seat);
        }
        return state;
    }

    public boolean hasSeat(SeatId seat) {
        return seatStates.containsKey(seat);
    }

    /** The top of a library, or empty when it is empty. Nothing here decides what that means. */
    public Optional<CardInstanceId> topOf(ZoneRef ref) {
        List<CardInstanceId> contents = contents(ref);
        return contents.isEmpty() ? Optional.empty() : Optional.of(contents.get(0));
    }

    /** The seat after this one in seating order, wrapping. */
    public SeatId seatAfter(SeatId seat) {
        int index = seats.indexOf(seat);
        if (index < 0) {
            throw new IllegalArgumentException("No such seat in this session: " + seat);
        }
        return seats.get((index + 1) % seats.size());
    }

    // ------------------------------------------------------------ transitions

    public GameState withCard(CardInstance card) {
        Map<CardInstanceId, CardInstance> updated = new LinkedHashMap<>(cards);
        updated.put(card.id(), card);
        return new GameState(
                seats, updated, zones, seatStates, peeks, revealed, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    /** Adds a card to the session and drops it into a zone in one step. */
    public GameState addCard(CardInstance card, ZoneRef into, Placement placement) {
        return withCard(card).place(card.id(), into, placement);
    }

    /**
     * Moves a card to a zone, taking it out of wherever it was.
     *
     * <p>Also settles where the card now sits. A card arriving on a surface gets the spot
     * it was dropped on, or one fanned out beside what is already down when a verb rather
     * than a hand put it there - so a card always has a definite place and every client draws
     * the same board instead of each inventing a layout. A card going back into a pile
     * forgets where it sat, because a pile is an order rather than a place.
     *
     * <p>Silently a no-op-with-a-move if the card was nowhere: adding a token straight to the
     * battlefield goes through the same path as moving one, and both should work.
     */
    public GameState place(CardInstanceId id, ZoneRef into, Placement placement) {
        Map<ZoneRef, List<CardInstanceId>> updated = new LinkedHashMap<>();
        for (Map.Entry<ZoneRef, List<CardInstanceId>> entry : zones.entrySet()) {
            List<CardInstanceId> contents = entry.getValue();
            updated.put(entry.getKey(), contents.contains(id) ? without(contents, id) : contents);
        }
        List<CardInstanceId> destination = new ArrayList<>(updated.getOrDefault(into, List.of()));
        if (placement.isTop()) {
            destination.add(0, id);
        } else {
            destination.add(id);
        }
        updated.put(into, List.copyOf(destination));

        GameState moved = new GameState(
                seats, cards, updated, seatStates, peeks, revealed, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
        return moved.settlePosition(id, into, placement);
    }

    /** Gives an arriving card its spot, or takes one away from a card joining a pile. */
    private GameState settlePosition(CardInstanceId id, ZoneRef into, Placement placement) {
        CardInstance card = cards.get(id);
        if (card == null) {
            return this;
        }
        if (!into.zone().isSurface()) {
            return withCard(card.withPosition(null));
        }
        TablePosition where = placement.chosenPosition().orElseGet(() -> unaimedSpot(into, id));
        return withCard(card.withPosition(where));
    }

    /**
     * Somewhere to put a card that arrived without being aimed.
     *
     * <p>A token appearing, a card put down by a verb rather than by a hand. Two cards may
     * sit on top of each other - a player stacking them means something by it and the mod
     * never says no - but the game must never do the stacking itself, because a permanent
     * hidden under another one reads as a card that failed to arrive. So this walks the fan
     * and takes the first spot nothing is already on, which also means a board that has had
     * things die does not drift across the table.
     *
     * <p>Only where the card sits counts, not the angle it is at: a spot occupied by a
     * sideways card is occupied.
     */
    public TablePosition unaimedSpot(ZoneRef ref, CardInstanceId ignoring) {
        Set<Long> taken = new HashSet<>();
        for (CardInstanceId occupant : contents(ref)) {
            if (occupant.equals(ignoring)) {
                continue;
            }
            CardInstance card = cards.get(occupant);
            if (card != null && card.position() != null) {
                taken.add(spotKey(card.position()));
            }
        }
        for (int index = 0; index < TablePosition.FAN_SPOTS; index++) {
            TablePosition candidate = TablePosition.unaimed(index);
            if (!taken.contains(spotKey(candidate))) {
                return candidate;
            }
        }
        // Every fan spot is occupied, which takes hundreds of permanents on one side. Landing
        // on top of something beats refusing to put the card down at all.
        return TablePosition.unaimed(taken.size());
    }

    private static long spotKey(TablePosition position) {
        return (long) position.x() * (TablePosition.SPAN + 1) + position.y();
    }

    /** Replaces a zone's contents wholesale. How a shuffle, a scry, and a reorder all land. */
    public GameState withZone(ZoneRef ref, List<CardInstanceId> contents) {
        Map<ZoneRef, List<CardInstanceId>> updated = new LinkedHashMap<>(zones);
        updated.put(ref, List.copyOf(contents));
        return new GameState(
                seats, cards, updated, seatStates, peeks, revealed, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    /** Takes a card out of the session entirely. Only tokens and copies ever leave this way. */
    public GameState removeCard(CardInstanceId id) {
        Map<CardInstanceId, CardInstance> updatedCards = new LinkedHashMap<>(cards);
        updatedCards.remove(id);
        Map<ZoneRef, List<CardInstanceId>> updatedZones = new LinkedHashMap<>();
        for (Map.Entry<ZoneRef, List<CardInstanceId>> entry : zones.entrySet()) {
            List<CardInstanceId> contents = entry.getValue();
            updatedZones.put(entry.getKey(), contents.contains(id) ? without(contents, id) : contents);
        }
        return new GameState(
                seats, updatedCards, updatedZones, seatStates, peeks, revealed, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withSeatState(SeatState state) {
        Map<SeatId, SeatState> updated = new LinkedHashMap<>(seatStates);
        updated.put(state.seat(), state);
        return new GameState(
                seats, cards, zones, updated, peeks, revealed, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    /** Opens a library to one seat. A seat looks at one library at a time, like a person. */
    public GameState withPeek(SeatId looker, Peek peek) {
        Map<SeatId, Peek> updated = new LinkedHashMap<>(peeks);
        updated.put(looker, peek);
        return new GameState(
                seats, cards, zones, seatStates, updated, revealed, turn, nextCardId,
                shuffleOrdinal, markerOrdinal, ended);
    }

    /** Closes whatever this seat had open. */
    public GameState withoutPeekBy(SeatId looker) {
        if (!peeks.containsKey(looker)) {
            return this;
        }
        Map<SeatId, Peek> updated = new LinkedHashMap<>(peeks);
        updated.remove(looker);
        return new GameState(
                seats, cards, zones, seatStates, updated, revealed, turn, nextCardId,
                shuffleOrdinal, markerOrdinal, ended);
    }

    /**
     * Closes every look at one library.
     *
     * <p>What a shuffle does. Whatever anybody had open is no longer what is in front of
     * them, and leaving it open would show them a library they are no longer looking at.
     */
    public GameState withoutPeeksAt(SeatId library) {
        Map<SeatId, Peek> updated = new LinkedHashMap<>();
        peeks.forEach((looker, peek) -> {
            if (!peek.at().equals(library)) {
                updated.put(looker, peek);
            }
        });
        if (updated.size() == peeks.size()) {
            return this;
        }
        return new GameState(
                seats, cards, zones, seatStates, updated, revealed, turn, nextCardId,
                shuffleOrdinal, markerOrdinal, ended);
    }

    /** Turns the top of a library face up to everybody, or face down again at zero. */
    public GameState withRevealed(SeatId library, int count) {
        Map<SeatId, Integer> updated = new LinkedHashMap<>(revealed);
        if (count <= 0) {
            updated.remove(library);
        } else {
            updated.put(library, count);
        }
        return new GameState(
                seats, cards, zones, seatStates, peeks, updated, turn, nextCardId, shuffleOrdinal,
                markerOrdinal, ended);
    }

    public GameState withTurn(TurnMarker newTurn) {
        return new GameState(
                seats, cards, zones, seatStates, peeks, revealed, newTurn, nextCardId,
                shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withNextCardId(int newNextCardId) {
        return new GameState(
                seats, cards, zones, seatStates, peeks, revealed, turn, newNextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withShuffleOrdinal(int newShuffleOrdinal) {
        return new GameState(
                seats, cards, zones, seatStates, peeks, revealed, turn, nextCardId, newShuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withMarkerOrdinal(int newMarkerOrdinal) {
        return new GameState(
                seats, cards, zones, seatStates, peeks, revealed, turn, nextCardId, shuffleOrdinal, newMarkerOrdinal, ended);
    }

    public GameState asEnded() {
        return ended
                ? this
                : new GameState(
                        seats, cards, zones, seatStates, peeks, revealed, turn, nextCardId, shuffleOrdinal,
                        markerOrdinal, true);
    }

    /** Cards grouped by the zone kind they sit in, for the renderers and for tests. */
    public Map<Zone, Integer> countsFor(SeatId seat) {
        Map<Zone, Integer> counts = new EnumMap<>(Zone.class);
        for (Zone zone : Zone.values()) {
            counts.put(zone, count(ZoneRef.of(seat, zone)));
        }
        return counts;
    }

    private static List<CardInstanceId> without(List<CardInstanceId> contents, CardInstanceId id) {
        List<CardInstanceId> copy = new ArrayList<>(contents);
        copy.remove(id);
        return List.copyOf(copy);
    }

    private static Map<ZoneRef, List<CardInstanceId>> deepImmutable(Map<ZoneRef, List<CardInstanceId>> source) {
        Map<ZoneRef, List<CardInstanceId>> copy = new LinkedHashMap<>();
        source.forEach((ref, contents) -> copy.put(ref, List.copyOf(contents)));
        return Collections.unmodifiableMap(copy);
    }
}
