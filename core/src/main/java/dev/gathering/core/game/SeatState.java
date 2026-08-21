package dev.gathering.core.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numbers beside a seat: life, commander damage taken, commander tax, and whether the
 * player has conceded.
 *
 * <p>Every one of these is a displayed number that players change by hand. Nothing here
 * triggers anything. A player on zero life is a player on zero life; the mod does not
 * declare them out, because deciding a game has ended is a rules judgement and there is no
 * rules engine here.
 *
 * @param commanderDamage damage taken from each other seat's commanders, the 21-point grid
 * @param commanderTax    per commander, the number of times it has been cast from the
 *                        command zone; displayed, never charged
 */
public record SeatState(
        SeatId seat,
        PlayerRef occupant,
        int life,
        Map<SeatId, Integer> commanderDamage,
        Map<CardInstanceId, Integer> commanderTax,
        boolean conceded) {

    public SeatState {
        if (seat == null) {
            throw new IllegalArgumentException("A seat state needs a seat");
        }
        commanderDamage = immutable(commanderDamage);
        commanderTax = immutable(commanderTax);
    }

    public static SeatState startingAt(SeatId seat, int startingLife) {
        return new SeatState(seat, null, startingLife, Map.of(), Map.of(), false);
    }

    /** A seat is held until the player leaves it, not until they walk away or log out. */
    public SeatState occupiedBy(PlayerRef player) {
        return new SeatState(seat, player, life, commanderDamage, commanderTax, conceded);
    }

    public SeatState released() {
        return new SeatState(seat, null, life, commanderDamage, commanderTax, conceded);
    }

    public java.util.Optional<PlayerRef> player() {
        return java.util.Optional.ofNullable(occupant);
    }

    public boolean isOccupied() {
        return occupant != null;
    }

    public SeatState withLife(int delta) {
        return new SeatState(seat, occupant, life + delta, commanderDamage, commanderTax, conceded);
    }

    public SeatState withCommanderDamage(SeatId from, int delta) {
        Map<SeatId, Integer> updated = new LinkedHashMap<>(commanderDamage);
        int now = updated.getOrDefault(from, 0) + delta;
        if (now == 0) {
            updated.remove(from);
        } else {
            updated.put(from, now);
        }
        return new SeatState(seat, occupant, life, updated, commanderTax, conceded);
    }

    public SeatState withCommanderTax(CardInstanceId commander, int delta) {
        Map<CardInstanceId, Integer> updated = new LinkedHashMap<>(commanderTax);
        int now = Math.max(0, updated.getOrDefault(commander, 0) + delta);
        if (now == 0) {
            updated.remove(commander);
        } else {
            updated.put(commander, now);
        }
        return new SeatState(seat, occupant, life, commanderDamage, updated, conceded);
    }

    public SeatState withConcede() {
        return conceded ? this : new SeatState(seat, occupant, life, commanderDamage, commanderTax, true);
    }

    public int commanderDamageFrom(SeatId from) {
        return commanderDamage.getOrDefault(from, 0);
    }

    public int taxOn(CardInstanceId commander) {
        return commanderTax.getOrDefault(commander, 0);
    }

    private static <K> Map<K, Integer> immutable(Map<K, Integer> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
