package dev.gathering.core.game.visibility;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** One seat as one viewer sees it: the numbers beside it and its six zones. */
public record SeatView(
        SeatId seat,
        PlayerRef player,
        PlayerRef lastPlayer,
        int life,
        Map<SeatId, Integer> commanderDamage,
        Map<CardInstanceId, Integer> commanderTax,
        Map<String, Integer> counters,
        boolean conceded,
        Map<Zone, ZoneView> zones) {

    public SeatView {
        commanderDamage = commanderDamage == null ? Map.of() : Map.copyOf(commanderDamage);
        commanderTax = commanderTax == null ? Map.of() : Map.copyOf(commanderTax);
        // Kept in the order they arrived rather than in a hash order, because a screen draws
        // them as rows and rows that reorder themselves are rows nobody can point at.
        counters = counters == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(counters));
        // Sorted into zone order, so two views of the same board list their zones the same
        // way. Built from the enum rather than from the map: EnumMap cannot be handed an
        // empty map - it has nothing to infer the key type from and throws - so a seat with
        // no zones at all, which is what a board still being assembled looks like, took a
        // screen down rather than being empty.
        EnumMap<Zone, ZoneView> sorted = new EnumMap<>(Zone.class);
        if (zones != null) {
            sorted.putAll(zones);
        }
        zones = Map.copyOf(sorted);
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    public Optional<PlayerRef> occupant() {
        return Optional.ofNullable(player);
    }

    /**
     * Whose board this is: whoever is sitting here, or failing that whoever last was.
     *
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
     *
     * <p>Leaving a seat releases the chair and leaves the cards exactly where they were, so a
     * board can outlast its player: they walked away mid-game, or the session is waiting for
     * somebody to come back. Drawing the furniture only for an occupied seat made that board
     * a battlefield with no zones behind it - the graveyard and the exile pile, which are
     * public and which somebody watching is there to read, simply stopped being on the table.
     *
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
