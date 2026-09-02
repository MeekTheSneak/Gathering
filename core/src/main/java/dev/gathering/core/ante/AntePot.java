package dev.gathering.core.ante;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.SeatId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cards on the table, and whose they were.
 * <p>Face up in the middle for the whole game, which is the entire feature: a pot with a real
 * rare in it is what makes a server's ante night a thing people talk about. So the pot is
 * state rather than a note - it is drawn, it is logged, and it survives a restart.
 * <p>Who staked what is kept, not just what is in there. Two resolutions need it: a session
 * that ends properly hands the lot to one seat, and a session that is voided - by vote, or by
 * a crash - hands every card back to the person it came from. The second is why a pot cannot
 * simply be a pile.
 */
public record AntePot(Map<SeatId, List<CardIdentity>> stakes) {

    public static final AntePot EMPTY = new AntePot(Map.of());

    /** Where a pot's cards end up: each seat and what it receives. */
    public record Payout(Map<SeatId, List<CardIdentity>> to) {

        public Payout {
            // Order kept. Map.copyOf hands back an unordered map, which for something that
            // is logged, drawn and written to disk means the pot reads differently every
            // time the game is loaded.
            to = to == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(to));
        }

        public List<CardIdentity> forSeat(SeatId seat) {
            return to.getOrDefault(seat, List.of());
        }

        /** Every card paid out, however it was split. */
        public List<CardIdentity> everything() {
            List<CardIdentity> all = new ArrayList<>();
            to.values().forEach(all::addAll);
            return List.copyOf(all);
        }
    }

    public AntePot {
        Map<SeatId, List<CardIdentity>> copied = new LinkedHashMap<>();
        if (stakes != null) {
            stakes.forEach((seat, cards) -> {
                if (seat != null && cards != null && !cards.isEmpty()) {
                    copied.put(seat, List.copyOf(cards));
                }
            });
        }
        // Insertion order is the order the seats staked, which is the order the pot is drawn
        // in and logged in. Map.copyOf would throw it away.
        stakes = java.util.Collections.unmodifiableMap(copied);
    }

    /** A pot with one more seat's stake in it. */
    public AntePot with(SeatId seat, List<CardIdentity> staked) {
        if (seat == null || staked == null || staked.isEmpty()) {
            return this;
        }
        Map<SeatId, List<CardIdentity>> next = new LinkedHashMap<>(stakes);
        List<CardIdentity> already = next.getOrDefault(seat, List.of());
        List<CardIdentity> both = new ArrayList<>(already);
        both.addAll(staked);
        next.put(seat, List.copyOf(both));
        return new AntePot(next);
    }

    public boolean isEmpty() {
        return stakes.isEmpty();
    }

    /** How many cards are in the pot, across every seat. */
    public int size() {
        int total = 0;
        for (List<CardIdentity> staked : stakes.values()) {
            total += staked.size();
        }
        return total;
    }

    /** What this seat put up. */
    public List<CardIdentity> stakeOf(SeatId seat) {
        return stakes.getOrDefault(seat, List.of());
    }

    /** Every card in the pot, in the order the seats staked them. */
    public List<CardIdentity> everything() {
        List<CardIdentity> all = new ArrayList<>(size());
        stakes.values().forEach(all::addAll);
        return List.copyOf(all);
    }

    /**
     * The whole pot to one seat.
     * <p>A winner who is not at this table gets nothing rather than an empty payout that
     * quietly loses the cards: a pot paid to nobody has to be a pot that stays put, because
     * the alternative is cards leaving the game with no owner.
     */
    public Payout toWinner(SeatId winner) {
        if (winner == null || isEmpty()) {
            return backToOwners();
        }
        return new Payout(Map.of(winner, everything()));
    }

    /**
     * Every card back where it came from.
     * <p>A voided session, a table taken apart, a server that came down mid-game. The escrow
     * is the point: a pot that could be eaten by a crash is a pot nobody sensible would put a
     * card into, and then the feature does not exist.
     */
    public Payout backToOwners() {
        return new Payout(new LinkedHashMap<>(stakes));
    }
}
