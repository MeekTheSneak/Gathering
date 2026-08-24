package dev.gathering.core.ui;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What moved between one look at a board and the next.
 *
 * <p>Cards were teleporting: a drawn card was in a library and then it was in a hand, with
 * nothing in between, and the only account anybody got of it was a line of text. A table where
 * things move is a table you can follow without reading, which is most of what watching a game
 * of Magic is.
 *
 * <p>Worked out by comparing two boards rather than by being told, and that is the whole
 * trick. A client is only ever sent what it may know: the owner of a library sees its cards
 * and nobody else does, so a draw looks like a card changing zones to one player and like two
 * numbers changing to everybody else. Both are enough to say a card went from there to here,
 * which is all a moving picture needs - so the movement is visible to the whole table without
 * one card's identity crossing to anybody the visibility rules did not already send it to.
 *
 * <p>Pure, and deliberately so: this is arithmetic on two snapshots, and the interesting cases
 * - a card nobody can see, several moves at once, a shuffle that changes no counts at all -
 * are all reachable from a test rather than from a game.
 */
public final class CardTravel {

    private CardTravel() {
    }

    /**
     * How long two sightings of a board can be apart and still be a difference.
     *
     * <p>A table stops sending boards the moment it is out of range or its chunk unloads, and
     * whatever it last sent stays in memory. Comparing against that when it comes back would
     * take every card that moved in the meantime and set them all off at once - and the
     * leftover counts would be paired across seats that had nothing to do with each other.
     * Comfortably longer than the two seconds a table pushes on by itself.
     */
    public static final long WORTH_COMPARING = 8_000L;

    /**
     * Whether two sightings this far apart are a difference or a first sighting.
     *
     * <p>Here rather than beside the map that holds the sightings, because it is arithmetic
     * about what a difference means - which is this class's whole subject - and because a
     * rule kept next to a client-side cache is a rule nothing can test.
     */
    public static boolean worthComparing(long apart) {
        return apart >= 0 && apart <= WORTH_COMPARING;
    }

    /**
     * One zone as a viewer sees it: how many cards, which of them it may name, and - for the
     * battlefield, where a card has a spot rather than a place in an order - where each of
     * the named ones is sitting.
     */
    public record Held(
            int count, List<CardInstanceId> seen, Map<CardInstanceId, TablePosition> spots) {

        public Held {
            seen = List.copyOf(seen);
            spots = spots == null ? Map.of() : Map.copyOf(spots);
        }

        /** A pile, where a card has no spot of its own. */
        public Held(int count, List<CardInstanceId> seen) {
            this(count, seen, Map.of());
        }

        public static final Held NOTHING = new Held(0, List.of(), Map.of());
    }

    /** Where a card is: whose zone, and which. */
    public record Place(SeatId seat, Zone zone) {
    }

    /**
     * One card going from one place to another.
     *
     * <p>The card is named only when the viewer could already name it in one of the two
     * places. A move nobody may see the identity of is still a move, and is still worth
     * drawing - as a sleeve.
     */
    public record Move(
            Place from, Place to, Optional<CardInstanceId> card,
            Optional<TablePosition> fromSpot, Optional<TablePosition> toSpot) {

        public Move {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            card = card == null ? Optional.empty() : card;
            fromSpot = fromSpot == null ? Optional.empty() : fromSpot;
            toSpot = toSpot == null ? Optional.empty() : toSpot;
        }

        /** A move between two piles, where neither end is a spot on a mat. */
        public Move(Place from, Place to, Optional<CardInstanceId> card) {
            this(from, to, card, Optional.empty(), Optional.empty());
        }
    }

    /**
     * The moves that turn one board into the other.
     *
     * <p>Named cards first, because a card the viewer can follow by name is followed exactly.
     * What is left over is matched by counts: a zone that lost two and a zone that gained two
     * is two cards going from the first to the second, and preferring a move within one seat
     * keeps a draw from being drawn as a card crossing the table.
     */
    public static List<Move> between(
            Map<Place, Held> before, Map<Place, Held> after) {
        List<Move> moves = new ArrayList<>();
        Map<Place, Integer> lost = new LinkedHashMap<>();
        Map<Place, Integer> gained = new LinkedHashMap<>();

        Map<CardInstanceId, Place> wasAt = placesOf(before);
        Map<CardInstanceId, Place> nowAt = placesOf(after);

        for (Map.Entry<CardInstanceId, Place> entry : wasAt.entrySet()) {
            CardInstanceId card = entry.getKey();
            Place was = entry.getValue();
            Place to = nowAt.get(card);
            if (to == null) {
                continue;
            }
            Optional<TablePosition> left = spotOf(before, was, card);
            Optional<TablePosition> arrived = spotOf(after, to, card);
            if (!to.equals(was)) {
                moves.add(new Move(was, to, Optional.of(card), left, arrived));
                lost.merge(was, 1, Integer::sum);
                gained.merge(to, 1, Integer::sum);
            } else if (movedOnTheSpot(left, arrived)) {
                // Same zone, different place on it. A creature pushed forward to attack has
                // not changed zones and has certainly moved, and for everybody but the
                // player whose hand did it there was nothing between the two spots at all.
                moves.add(new Move(was, to, Optional.of(card), left, arrived));
            }
        }

        // What the counts say, minus what the named cards already accounted for.
        Map<Place, Integer> spare = new LinkedHashMap<>();
        for (Place place : everywhere(before, after)) {
            int change = held(after, place).count() - held(before, place).count();
            int alreadySaid = gained.getOrDefault(place, 0) - lost.getOrDefault(place, 0);
            int left = change - alreadySaid;
            if (left != 0) {
                spare.put(place, left);
            }
        }

        List<Place> emptied = sorted(spare, amount -> amount < 0);
        List<Place> filled = sorted(spare, amount -> amount > 0);
        for (Place to : filled) {
            int wanted = spare.get(to);
            while (wanted > 0) {
                Place from = nearestSpare(emptied, spare, to);
                if (from == null) {
                    break;
                }
                moves.add(new Move(from, to, Optional.empty()));
                spare.merge(from, 1, Integer::sum);
                wanted--;
            }
        }
        return List.copyOf(moves);
    }

    /**
     * Whether these two spots are far enough apart to be a card being moved.
     *
     * <p>Coordinates only. A {@link TablePosition} carries an angle as well, and turning a
     * card is not moving it: tapping every permanent you have would otherwise be a board's
     * worth of cards flying from where they are to where they already are.
     */
    private static boolean movedOnTheSpot(
            Optional<TablePosition> from, Optional<TablePosition> to) {
        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }
        return from.get().x() != to.get().x() || from.get().y() != to.get().y();
    }

    private static Optional<TablePosition> spotOf(
            Map<Place, Held> board, Place place, CardInstanceId card) {
        Held held = board.get(place);
        return held == null ? Optional.empty() : Optional.ofNullable(held.spots().get(card));
    }

    /** A zone that has cards to spare, preferring one belonging to the same player. */
    private static Place nearestSpare(
            List<Place> emptied, Map<Place, Integer> spare, Place to) {
        Place any = null;
        for (Place from : emptied) {
            if (spare.getOrDefault(from, 0) >= 0) {
                continue;
            }
            if (from.seat().equals(to.seat())) {
                return from;
            }
            if (any == null) {
                any = from;
            }
        }
        return any;
    }

    private static Map<CardInstanceId, Place> placesOf(Map<Place, Held> board) {
        Map<CardInstanceId, Place> where = new HashMap<>();
        for (Map.Entry<Place, Held> entry : board.entrySet()) {
            for (CardInstanceId id : entry.getValue().seen()) {
                where.put(id, entry.getKey());
            }
        }
        return where;
    }

    private static Held held(Map<Place, Held> board, Place place) {
        return board.getOrDefault(place, Held.NOTHING);
    }

    /** Every place either board mentions, in a settled order so two runs agree. */
    private static List<Place> everywhere(Map<Place, Held> before, Map<Place, Held> after) {
        List<Place> places = new ArrayList<>(before.keySet());
        for (Place place : after.keySet()) {
            if (!places.contains(place)) {
                places.add(place);
            }
        }
        places.sort(Comparator
                .comparingInt((Place place) -> place.seat().index())
                .thenComparing(place -> place.zone().ordinal()));
        return places;
    }

    private static List<Place> sorted(
            Map<Place, Integer> spare, java.util.function.IntPredicate wanted) {
        List<Place> places = new ArrayList<>();
        for (Map.Entry<Place, Integer> entry : spare.entrySet()) {
            if (wanted.test(entry.getValue())) {
                places.add(entry.getKey());
            }
        }
        places.sort(Comparator
                .comparingInt((Place place) -> place.seat().index())
                .thenComparing(place -> place.zone().ordinal()));
        return places;
    }
}
