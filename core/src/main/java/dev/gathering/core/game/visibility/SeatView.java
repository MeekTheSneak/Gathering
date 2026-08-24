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

    public ZoneView zone(Zone zone) {
        ZoneView view = zones.get(zone);
        if (view == null) {
            throw new IllegalStateException("A seat view is missing its " + zone + " zone");
        }
        return view;
    }
}
