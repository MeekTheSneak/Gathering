package dev.gathering.core.game.visibility;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * One seat as one viewer sees it: the numbers beside it and its six zones.
 *
 * @param handShownTo which other seats this player has turned their hand toward. Public to
 *     everybody, because turning your hand round is something the whole table watches you do
 *     - and because the person doing it has to be able to see, on their own screen, that
 *     their hand is still face up. A reveal nobody is reminded of is a hand left open.
 * @param sleeve what this seat's cards look like from behind. Public to everybody, because
 *     the whole point of a sleeve is that the table can tell whose cards are whose across it.
 */
public record SeatView(
        SeatId seat,
        PlayerRef player,
        PlayerRef lastPlayer,
        int life,
        Map<CardInstanceId, Integer> commanderDamage,
        Map<CardInstanceId, Integer> commanderTax,
        java.util.List<CardInstanceId> commanders,
        Map<String, Integer> counters,
        boolean conceded,
        java.util.Set<SeatId> handShownTo,
        dev.gathering.core.card.Sleeve sleeve,
        Map<Zone, ZoneView> zones) {

    public SeatView {
        // Kept in order, like the counters below and for a related reason: these are written
        // into the board's byte form by walking them, so a hash order salted once per launch
        // would encode the same board differently on every start.
        commanderDamage = commanderDamage == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(commanderDamage));
        commanderTax = commanderTax == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(commanderTax));
        // Which cards this seat brought as commanders, public to everyone: they went down
        // face up in the command zone in front of the whole table.
        commanders = commanders == null ? java.util.List.of() : java.util.List.copyOf(commanders);
        // Kept in the order they arrived rather than in a hash order, because a screen draws
        // them as rows and rows that reorder themselves are rows nobody can point at.
        counters = counters == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(counters));
        handShownTo = handShownTo == null || handShownTo.isEmpty()
                ? java.util.Set.of()
                : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(handShownTo));
        // A board still being assembled has no sleeve yet, and draws the ordinary back.
        sleeve = sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
        // Sorted into zone order, so two views of the same board list their zones the same
        // way. Built from the enum rather than from the map: EnumMap cannot be handed an
        // empty map - it has nothing to infer the key type from and throws - so a seat with
        // no zones at all, which is what a board still being assembled looks like, took a
        // screen down rather than being empty.
        EnumMap<Zone, ZoneView> sorted = new EnumMap<>(Zone.class);
        if (zones != null) {
            sorted.putAll(zones);
        }
        // The EnumMap itself, not a copy of it: Map.copyOf would order by a hash salted once
        // per launch and throw away the zone order this went to the trouble of building - so
        // the sentence above about two views listing their zones the same way was true of the
        // sort and false of what came out of it.
        zones = java.util.Collections.unmodifiableMap(sorted);
    }

    /** Whether this hand is currently readable by that seat. Its own seat always may. */
    public boolean handIsShownTo(SeatId other) {
        return other != null && (other.equals(seat) || handShownTo.contains(other));
    }

    /** Whether this player has turned their hand toward anybody. */
    public boolean handIsShown() {
        return !handShownTo.isEmpty();
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    public Optional<PlayerRef> occupant() {
        return Optional.ofNullable(player);
    }

    /**
     * Whose board this is: whoever is sitting here, or failing that whoever last was.
     * <p>What every sentence about a seat wants. Asking who holds the chair instead gave the
     * wrong answer the moment somebody stood up: their life total, the title over their
     * graveyard and every line they had already put in the log all became "(empty)", which
     * reads as though the game had forgotten they were ever there.
     */
    public Optional<PlayerRef> whoseBoard() {
        return Optional.ofNullable(player == null ? lastPlayer : player);
    }

    /**
     * Whether this seat has a board on the table, whether or not anybody is sitting at it.
     * <p>Leaving a seat releases the chair and leaves the cards exactly where they were, so a
     * board can outlast its player: they walked away mid-game, or the session is waiting for
     * somebody to come back. Drawing the furniture only for an occupied seat made that board
     * a battlefield with no zones behind it - the graveyard and the exile pile, which are
     * public and which somebody watching is there to read, simply stopped being on the table.
     * <p>A chair nobody has ever sat in still shows only its outline, which is the thing this
     * is careful to keep: it has no cards, so it has no board.
     */
    public boolean hasABoard() {
        if (player != null) {
            return true;
        }
        for (ZoneView held : zones.values()) {
            if (held.count() > 0) {
                return true;
            }
        }
        return false;
    }

    public ZoneView zone(Zone zone) {
        ZoneView view = zones.get(zone);
        if (view == null) {
            throw new IllegalStateException("A seat view is missing its " + zone + " zone");
        }
        return view;
    }
}
