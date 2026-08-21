package dev.gathering.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                seats, Map.of(), zones, seatStates, TurnMarker.start(seats.get(0)), 0, 0, 0, false);
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
                seats, updated, zones, seatStates, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    /** Adds a card to the session and drops it into a zone in one step. */
    public GameState addCard(CardInstance card, ZoneRef into, Placement placement) {
        return withCard(card).place(card.id(), into, placement);
    }

    /**
     * Moves a card to a zone, taking it out of wherever it was.
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
        return new GameState(
                seats, cards, updated, seatStates, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    /** Replaces a zone's contents wholesale. How a shuffle, a scry, and a reorder all land. */
    public GameState withZone(ZoneRef ref, List<CardInstanceId> contents) {
        Map<ZoneRef, List<CardInstanceId>> updated = new LinkedHashMap<>(zones);
        updated.put(ref, List.copyOf(contents));
        return new GameState(
                seats, cards, updated, seatStates, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
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
                seats, updatedCards, updatedZones, seatStates, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withSeatState(SeatState state) {
        Map<SeatId, SeatState> updated = new LinkedHashMap<>(seatStates);
        updated.put(state.seat(), state);
        return new GameState(
                seats, cards, zones, updated, turn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withTurn(TurnMarker newTurn) {
        return new GameState(
                seats, cards, zones, seatStates, newTurn, nextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withNextCardId(int newNextCardId) {
        return new GameState(
                seats, cards, zones, seatStates, turn, newNextCardId, shuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withShuffleOrdinal(int newShuffleOrdinal) {
        return new GameState(
                seats, cards, zones, seatStates, turn, nextCardId, newShuffleOrdinal, markerOrdinal, ended);
    }

    public GameState withMarkerOrdinal(int newMarkerOrdinal) {
        return new GameState(
                seats, cards, zones, seatStates, turn, nextCardId, shuffleOrdinal, newMarkerOrdinal, ended);
    }

    public GameState asEnded() {
        return ended
                ? this
                : new GameState(
                        seats, cards, zones, seatStates, turn, nextCardId, shuffleOrdinal, markerOrdinal, true);
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
