package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import dev.gathering.core.ui.CardTravel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * The cards currently in the air between two places on a table.
 *
 * <p>A card that is in a library and then in a hand, with nothing in between, is a card that
 * teleported - and a table where things teleport can only be followed by reading the log. So
 * every board that arrives is compared with the one before it, and whatever changed places is
 * drawn crossing the felt for a moment.
 *
 * <p>Held per table rather than per screen, because both views draw the same flights and a
 * table on a shelf across the room is showing a game somebody else is playing. Nothing here
 * knows anything a client was not already sent: see {@link CardTravel}.
 *
 * <p>Client-only, and touched from the network thread as well as the render thread - a board
 * arrives on one and is drawn on the other - so the maps are synchronized on this class.
 */
public final class ClientCardFlights {

    /**
     * How long a card takes to cross, in milliseconds.
     *
     * <p>Long enough to be followed by eye and short enough not to be waited for. A card that
     * takes half a second to reach a graveyard is a card somebody is watching instead of
     * playing; a card that takes a tenth is a flicker.
     *
     * <p>Measured against {@link net.minecraft.Util#getMillis()}, which counts from a
     * monotonic source rather than from the wall clock - a machine that syncs its clock
     * mid-flight must not leave a card hanging in the air.
     */
    public static final long CROSSING = 260L;

    /** One card on its way, with the moment it set off. */
    public record Flight(CardTravel.Move move, long began) {

        /** How far along it is, nought at the start and one at the end. */
        public float progress(long now) {
            long gone = now - began;
            return gone <= 0 ? 0f : Math.min(1f, gone / (float) CROSSING);
        }

        /**
         * Whether it has arrived.
         *
         * <p>A clock that has gone backwards counts as arrived. These times come from a
         * monotonic source so it should not happen, and if it ever does the failure has to
         * be a card that lands early rather than one frozen at its origin for the rest of
         * the session, never pruned because it can never finish.
         */
        public boolean landed(long now) {
            long gone = now - began;
            return gone < 0 || gone >= CROSSING;
        }
    }

    /**
     * The last board seen at each table, and when it was seen.
     *
     * <p>When matters. A table stops sending boards the moment it is out of range or its
     * chunk unloads, and the shape it last sent stays here - so walking back to a game half
     * an hour later would compare a board against one from before lunch. How long is too
     * long is {@link CardTravel#worthComparing(long)}'s to say.
     */
    private record Snapshot(Map<CardTravel.Place, CardTravel.Held> shape, long seen) {
    }

    private static final Map<BlockPos, Snapshot> SEEN = new HashMap<>();

    private static final Map<BlockPos, List<Flight>> FLYING = new HashMap<>();

    /**
     * Where each card was last seen sitting on a mat.
     *
     * <p>A creature that dies is not on the battlefield any more, so the board that arrives
     * cannot say where it was - and a card flying to a graveyard from the middle of a mat it
     * was never in the middle of reads as the wrong card moving. This is the only thing the
     * new board does not contain, so it is the only thing kept.
     */
    private static final Map<BlockPos, Map<CardInstanceId, dev.gathering.core.game.TablePosition>>
            LAST_SAT = new HashMap<>();

    /**
     * Cards this client is moving itself, which must not also be drawn flying.
     *
     * <p>A card dragged across the felt has already made the journey under the player's own
     * cursor; drawing a second copy of it setting off from where it started would be the
     * table disagreeing with the hand that moved it.
     */
    private static final Map<CardInstanceId, Long> OWN_DOING = new HashMap<>();

    /** How long a card this client moved stays exempt. One crossing plus a little slack. */
    private static final long OWN_DOING_LASTS = CROSSING * 3;

    private ClientCardFlights() {
    }

    /**
     * The clock every time in here is measured against.
     *
     * <p>Monotonic, and deliberately not the wall clock: a machine that syncs its time in the
     * middle of a card's flight would otherwise leave that card hanging in the air forever,
     * because it could never reach an end that had moved into the past.
     */
    public static long now() {
        return net.minecraft.Util.getMillis();
    }

    /** Notes that this client moved this card, so the board agreeing is not news. */
    public static void movedItOurselves(CardInstanceId card, long now) {
        if (card == null) {
            return;
        }
        synchronized (ClientCardFlights.class) {
            OWN_DOING.put(card, now);
        }
    }

    /**
     * Takes a new board and starts a flight for everything that changed places.
     *
     * <p>The first board a table sends starts nothing: everything on it is new, and a game
     * opening with a hundred cards flying out of nowhere is not what anybody meant by
     * following the action.
     */
    public static void arrived(BlockPos table, GameView board, long now) {
        Map<CardTravel.Place, CardTravel.Held> shape = shapeOf(board);
        Map<CardInstanceId, dev.gathering.core.game.TablePosition> sat = seatingOf(board);
        synchronized (ClientCardFlights.class) {
            Snapshot before = SEEN.put(table.immutable(), new Snapshot(shape, now));
            // Merged rather than replaced: the point of it is the cards that have just left.
            LAST_SAT.computeIfAbsent(table.immutable(), key -> new HashMap<>()).putAll(sat);
            if (before == null || !CardTravel.worthComparing(now - before.seen())) {
                return;
            }
            List<Flight> flying = FLYING.computeIfAbsent(table.immutable(), key -> new ArrayList<>());
            flying.removeIf(flight -> flight.landed(now));
            OWN_DOING.entrySet().removeIf(entry -> now - entry.getValue() > OWN_DOING_LASTS);
            for (CardTravel.Move move : CardTravel.between(before.shape(), shape)) {
                if (move.card().map(OWN_DOING::containsKey).orElse(false)) {
                    continue;
                }
                flying.add(new Flight(move, now));
            }
        }
    }

    /** What is in the air at this table, oldest first. Never null, often empty. */
    public static List<Flight> at(BlockPos table, long now) {
        synchronized (ClientCardFlights.class) {
            List<Flight> flying = FLYING.get(table);
            if (flying == null || flying.isEmpty()) {
                return List.of();
            }
            flying.removeIf(flight -> flight.landed(now));
            return List.copyOf(flying);
        }
    }

    /** Forgets a table, so a game left and rejoined does not open with a flock of cards. */
    public static void forget(BlockPos table) {
        synchronized (ClientCardFlights.class) {
            SEEN.remove(table);
            FLYING.remove(table);
            LAST_SAT.remove(table);
        }
    }

    /** Where this card was last seen sitting on a mat, if this client ever saw it there. */
    public static Optional<dev.gathering.core.game.TablePosition> lastSatAt(
            BlockPos table, CardInstanceId card) {
        if (card == null) {
            return Optional.empty();
        }
        synchronized (ClientCardFlights.class) {
            Map<CardInstanceId, dev.gathering.core.game.TablePosition> sat = LAST_SAT.get(table);
            return sat == null ? Optional.empty() : Optional.ofNullable(sat.get(card));
        }
    }

    public static void clear() {
        synchronized (ClientCardFlights.class) {
            SEEN.clear();
            FLYING.clear();
            LAST_SAT.clear();
            OWN_DOING.clear();
        }
    }

    /**
     * The board as two numbers per zone: how many cards, and which of them this client may
     * name. Everything {@link CardTravel} needs and nothing else.
     */
    private static Map<CardTravel.Place, CardTravel.Held> shapeOf(GameView board) {
        Map<CardTravel.Place, CardTravel.Held> shape = new LinkedHashMap<>();
        for (SeatView seat : board.seats()) {
            for (Zone zone : Zone.values()) {
                ZoneView contents = seat.zone(zone);
                if (contents == null) {
                    continue;
                }
                List<CardInstanceId> seen = new ArrayList<>();
                for (CardView card : contents.cards()) {
                    if (card instanceof CardView.Visible visible) {
                        seen.add(visible.id());
                    }
                }
                shape.put(new CardTravel.Place(seat.seat(), zone),
                        new CardTravel.Held(contents.count(), seen));
            }
        }
        return shape;
    }

    /** Where every card this client can see is sitting, so it can be remembered. */
    private static Map<CardInstanceId, dev.gathering.core.game.TablePosition> seatingOf(
            GameView board) {
        Map<CardInstanceId, dev.gathering.core.game.TablePosition> sat = new HashMap<>();
        for (SeatView seat : board.seats()) {
            ZoneView battlefield = seat.zone(Zone.BATTLEFIELD);
            if (battlefield == null) {
                continue;
            }
            for (CardView card : battlefield.cards()) {
                if (card instanceof CardView.Visible visible) {
                    card.placedAt().ifPresent(where -> sat.put(visible.id(), where));
                }
            }
        }
        return sat;
    }

    /** Which card, if the viewer may know, so a flight can be drawn face up. */
    public static Optional<CardInstanceId> nameOf(Flight flight) {
        return flight.move().card();
    }

    /** Whose zone a flight starts and ends in, for whatever is drawing it. */
    public static SeatId fromSeat(Flight flight) {
        return flight.move().from().seat();
    }

    public static SeatId toSeat(Flight flight) {
        return flight.move().to().seat();
    }
}
