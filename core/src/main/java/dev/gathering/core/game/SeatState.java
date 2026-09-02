package dev.gathering.core.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The numbers beside a seat: life, commander damage taken, commander tax, and whether the
 * player has conceded.
 *
 * <p>Every one of these is a displayed number that players change by hand. Nothing here
 * triggers anything. A player on zero life is a player on zero life; the mod does not
 * declare them out, because deciding a game has ended is a rules judgment and there is no
 * rules engine here.
 *
 * @param lastOccupant    whoever sat here most recently, kept after they leave so the board
 *                        they left behind still has a name on it
 * @param commanderDamage damage taken from each commander, by the card. By the card and not
 *                        by the seat, because the rule is 21 from the <em>same</em> commander
 *                        and a partner deck fields two: one number per enemy seat could not
 *                        tell Halana's damage from Tevesh's, which is exactly the pair the
 *                        rule exists to separate
 * @param commanders      the cards this seat brought as commanders, wherever they are now.
 *                        Recorded when the deck goes down, because a commander on the
 *                        battlefield is still the commander and nothing about its zone says so
 * @param commanderTax    per commander, the number of times it has been cast from the
 *                        command zone; displayed, never charged
 * @param counters        poison, energy, experience and anything else a player cares to name;
 *                        the same open-ended bag a card has, for the same reason - the set of
 *                        things a player can accumulate is not one anybody can finish listing
 * @param handShownTo     which other seats this hand is currently face up to. Empty for the
 *                        ordinary case, which is a hand only its owner can read. A player
 *                        showing their hand is a thing they do on purpose and take back on
 *                        purpose, so it is state on the seat rather than a moment in the log:
 *                        Duress is resolved by the person being Duressed pressing the button,
 *                        exactly as they would turn their hand round at a real table, and
 *                        nothing anybody else can submit ever opens somebody's hand
 * @param sleeve          what this player's cards look like from behind. Recorded when the
 *                        deck goes down, like the commanders and for the same reason: it is
 *                        a fact about what somebody brought rather than about where a card is
 */
public record SeatState(
        SeatId seat,
        PlayerRef occupant,
        PlayerRef lastOccupant,
        int life,
        Map<CardInstanceId, Integer> commanderDamage,
        Map<CardInstanceId, Integer> commanderTax,
        java.util.List<CardInstanceId> commanders,
        Map<String, Integer> counters,
        boolean conceded,
        Set<SeatId> handShownTo,
        dev.gathering.core.card.Sleeve sleeve) {

    public SeatState {
        if (seat == null) {
            throw new IllegalArgumentException("A seat state needs a seat");
        }
        commanderDamage = immutable(commanderDamage);
        commanderTax = immutable(commanderTax);
        commanders = commanders == null ? java.util.List.of() : java.util.List.copyOf(commanders);
        counters = counters == null || counters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(counters));
        // Kept in the order the seats were shown, for the same reason every other collection
        // here is: it is written into the board's byte form by walking it, and a hash order
        // salted once per launch would encode the same board differently on every start.
        handShownTo = handShownTo == null || handShownTo.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(handShownTo));
        // A seat with no sleeve is a seat whose deck has not gone down yet, and it draws the
        // ordinary back. Defaulted rather than rejected because this is read back off saved
        // logs written before sleeves existed.
        sleeve = sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
    }

    public static SeatState startingAt(SeatId seat, int startingLife) {
        return new SeatState(
                seat, null, null, startingLife, Map.of(), Map.of(), java.util.List.of(), Map.of(), false,
                Set.of(), dev.gathering.core.card.Sleeve.DEFAULT);
    }

    /** A seat is held until the player leaves it, not until they walk away or log out. */
    public SeatState occupiedBy(PlayerRef player) {
        return new SeatState(
                seat, player, player, life, commanderDamage, commanderTax, commanders, counters, conceded,
                handShownTo, sleeve);
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
                seat, null, lastOccupant, life, commanderDamage, commanderTax, commanders, counters, conceded,
                handShownTo, sleeve);
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
        return new SeatState(
                seat, occupant, lastOccupant, life + delta, commanderDamage, commanderTax, commanders, counters,
                conceded, handShownTo, sleeve);
    }

    public SeatState withCommanderDamage(CardInstanceId commander, int delta) {
        Map<CardInstanceId, Integer> updated = new LinkedHashMap<>(commanderDamage);
        int now = updated.getOrDefault(commander, 0) + delta;
        if (now == 0) {
            updated.remove(commander);
        } else {
            updated.put(commander, now);
        }
        return new SeatState(
                seat, occupant, lastOccupant, life, updated, commanderTax, commanders, counters, conceded,
                handShownTo, sleeve);
    }

    /** Sleeves this seat's cards, once, when the deck goes down. */
    public SeatState withSleeve(dev.gathering.core.card.Sleeve chosen) {
        return new SeatState(
                seat, occupant, lastOccupant, life, commanderDamage, commanderTax, commanders, counters,
                conceded, handShownTo, chosen);
    }

    /** Names this seat's commanders, once, when the deck goes down. */
    public SeatState withCommanders(java.util.List<CardInstanceId> named) {
        return new SeatState(
                seat, occupant, lastOccupant, life, commanderDamage, commanderTax, named, counters, conceded,
                handShownTo, sleeve);
    }

    public SeatState withCommanderTax(CardInstanceId commander, int delta) {
        Map<CardInstanceId, Integer> updated = new LinkedHashMap<>(commanderTax);
        int now = Math.max(0, updated.getOrDefault(commander, 0) + delta);
        if (now == 0) {
            updated.remove(commander);
        } else {
            updated.put(commander, now);
        }
        return new SeatState(
                seat, occupant, lastOccupant, life, commanderDamage, updated, commanders, counters, conceded,
                handShownTo, sleeve);
    }

    public SeatState withConcede() {
        return conceded
                ? this
                : new SeatState(
                        seat, occupant, lastOccupant, life, commanderDamage, commanderTax, commanders, counters, true,
                        handShownTo, sleeve);
    }

    /**
     * Adds to a named counter beside this seat, dropping the entry when it reaches zero.
     *
     * <p>Poison, energy, experience, the day/night marker somebody is tracking by hand. Goes
     * negative as freely as everything else here, because a player who wants minus two energy
     * has a reason and the mod does not argue.
     */
    public SeatState withCounter(String name, int delta) {
        if (name == null) {
            return this;
        }
        Map<String, Integer> updated = new LinkedHashMap<>(counters);
        // The same bound a card has, for the same reason: a new name is a new key, and the
        // board these end up on goes out as one payload with a bound that throws.
        if (!updated.containsKey(name) && updated.size() >= CounterName.MOST_PER_CARD) {
            return this;
        }
        int now = updated.getOrDefault(name, 0) + delta;
        if (now == 0) {
            updated.remove(name);
        } else {
            updated.put(name, now);
        }
        return new SeatState(
                seat, occupant, lastOccupant, life, commanderDamage, commanderTax, commanders, updated, conceded,
                handShownTo, sleeve);
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    /**
     * Turns this hand face up to another seat, or face down again.
     *
     * <p>Adding rather than replacing, so showing Bob and then showing Chris shows both -
     * which is what happens at a table, where turning your hand toward one more person does
     * not turn it away from the last one.
     */
    public SeatState withHandShownTo(SeatId other, boolean showing) {
        if (other == null || other.equals(seat) || showing == handShownTo.contains(other)) {
            return this;
        }
        Set<SeatId> updated = new LinkedHashSet<>(handShownTo);
        if (showing) {
            updated.add(other);
        } else {
            updated.remove(other);
        }
        return new SeatState(
                seat, occupant, lastOccupant, life, commanderDamage, commanderTax, commanders, counters,
                conceded, updated, sleeve);
    }

    /** Turns this hand face up to all of these seats at once, or face down to everybody. */
    public SeatState withHandShownTo(Set<SeatId> others) {
        Set<SeatId> without = new LinkedHashSet<>(others == null ? Set.of() : others);
        without.remove(seat);
        return without.equals(handShownTo)
                ? this
                : new SeatState(
                        seat, occupant, lastOccupant, life, commanderDamage, commanderTax, commanders,
                        counters, conceded, without, sleeve);
    }

    /** Whether that seat may currently read this hand. Its own seat always may. */
    public boolean handIsShownTo(SeatId other) {
        return other != null && (other.equals(seat) || handShownTo.contains(other));
    }

    /** Whether anybody at all is being shown this hand. */
    public boolean handIsShown() {
        return !handShownTo.isEmpty();
    }

    /** The counters a player accumulates often enough to be worth spelling once. */
    public static final class Counters {
        public static final String POISON = "poison";
        public static final String ENERGY = "energy";
        public static final String EXPERIENCE = "experience";

        private Counters() {
        }
    }

    private static <K> Map<K, Integer> immutable(Map<K, Integer> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
