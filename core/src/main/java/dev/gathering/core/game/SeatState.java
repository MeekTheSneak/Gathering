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
 * @param lastOccupant    whoever sat here most recently, kept after they leave so the board
 *                        they left behind still has a name on it
 * @param commanderDamage damage taken from each other seat's commanders, the 21-point grid
 * @param commanderTax    per commander, the number of times it has been cast from the
 *                        command zone; displayed, never charged
 * @param counters        poison, energy, experience and anything else a player cares to name;
 *                        the same open-ended bag a card has, for the same reason - the set of
 *                        things a player can accumulate is not one anybody can finish listing
 */
public record SeatState(
        SeatId seat,
        PlayerRef occupant,
        PlayerRef lastOccupant,
        int life,
        Map<SeatId, Integer> commanderDamage,
        Map<CardInstanceId, Integer> commanderTax,
        Map<String, Integer> counters,
        boolean conceded) {

    public SeatState {
        if (seat == null) {
            throw new IllegalArgumentException("A seat state needs a seat");
        }
        commanderDamage = immutable(commanderDamage);
        commanderTax = immutable(commanderTax);
        counters = counters == null || counters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }

    public static SeatState startingAt(SeatId seat, int startingLife) {
        return new SeatState(seat, null, null, startingLife, Map.of(), Map.of(), Map.of(), false);
    }

    /** A seat is held until the player leaves it, not until they walk away or log out. */
    public SeatState occupiedBy(PlayerRef player) {
        return new SeatState(
                seat, player, player, life, commanderDamage, commanderTax, counters, conceded);
    }

    /**
     * Lets the chair go, and remembers who was in it.
     *
     * <p>The cards stay where they are, so the board outlasts the player - and a board with
     * nobody's name on it is a board nobody can talk about. Everything the log has already
     * recorded them doing is written against this seat, so forgetting the name here rewrites
     * the whole history of the game as things "(empty)" did.
     */
    public SeatState released() {
        return new SeatState(
                seat, null, lastOccupant, life, commanderDamage, commanderTax, counters, conceded);
    }

    public java.util.Optional<PlayerRef> player() {
        return java.util.Optional.ofNullable(occupant);
    }

    public boolean isOccupied() {
        return occupant != null;
    }

    /**
     * Whose board this is: whoever is sitting here, or failing that whoever last was.
     *
     * <p>Not the same question as who holds the chair. A seat somebody walked away from is
     * free for the next player and still covered in the last one's cards, and every sentence
     * anybody writes about it - the log, a life total, the title over a pile - wants the name
     * rather than the chair.
     */
    public java.util.Optional<PlayerRef> whoseBoard() {
        return java.util.Optional.ofNullable(occupant == null ? lastOccupant : occupant);
    }

    public SeatState withLife(int delta) {
        return new SeatState(seat, occupant, lastOccupant, life + delta, commanderDamage, commanderTax, counters, conceded);
    }

    public SeatState withCommanderDamage(SeatId from, int delta) {
        Map<SeatId, Integer> updated = new LinkedHashMap<>(commanderDamage);
        int now = updated.getOrDefault(from, 0) + delta;
        if (now == 0) {
            updated.remove(from);
        } else {
            updated.put(from, now);
        }
        return new SeatState(seat, occupant, lastOccupant, life, updated, commanderTax, counters, conceded);
    }

    public SeatState withCommanderTax(CardInstanceId commander, int delta) {
        Map<CardInstanceId, Integer> updated = new LinkedHashMap<>(commanderTax);
        int now = Math.max(0, updated.getOrDefault(commander, 0) + delta);
        if (now == 0) {
            updated.remove(commander);
        } else {
            updated.put(commander, now);
        }
        return new SeatState(seat, occupant, lastOccupant, life, commanderDamage, updated, counters, conceded);
    }

    public SeatState withConcede() {
        return conceded
                ? this
                : new SeatState(seat, occupant, lastOccupant, life, commanderDamage, commanderTax, counters, true);
    }

    /**
     * Adds to a named counter beside this seat, dropping the entry when it reaches zero.
     *
     * <p>Poison, energy, experience, the day/night marker somebody is tracking by hand. Goes
     * negative as freely as everything else here, because a player who wants minus two energy
     * has a reason and the mod does not argue.
     */
    public SeatState withCounter(String name, int delta) {
        Map<String, Integer> updated = new LinkedHashMap<>(counters);
        int now = updated.getOrDefault(name, 0) + delta;
        if (now == 0) {
            updated.remove(name);
        } else {
            updated.put(name, now);
        }
        return new SeatState(seat, occupant, lastOccupant, life, commanderDamage, commanderTax, updated, conceded);
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    /** The counters a player accumulates often enough to be worth spelling once. */
    public static final class Counters {
        public static final String POISON = "poison";
        public static final String ENERGY = "energy";
        public static final String EXPERIENCE = "experience";

        private Counters() {
        }
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
