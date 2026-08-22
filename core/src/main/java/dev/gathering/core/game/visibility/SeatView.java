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
        counters = counters == null ? Map.of() : Map.copyOf(counters);
        zones = zones == null ? Map.of() : Map.copyOf(new EnumMap<>(zones));
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
