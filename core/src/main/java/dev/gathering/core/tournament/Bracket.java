package dev.gathering.core.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single elimination cut after the Swiss rounds.
 * <p>Seeded from the Swiss standings so the best-placed players meet as late as possible: in a
 * top eight, first plays eighth and the winner meets whoever wins fourth against fifth. Every
 * later round pairs the winners of neighboring matches, in bracket order.
 */
public final class Bracket {

    private Bracket() {
    }

    /** The first round of a cut of 4 or 8, from players already ordered best first. */
    public static List<Pairing> firstRound(List<UUID> seeded) {
        int size = seeded.size();
        if (size != 2 && size != 4 && size != 8) {
            throw new IllegalArgumentException("A cut is 2, 4 or 8 players, not " + size);
        }
        List<Integer> order = bracketOrder(size);
        List<Pairing> pairings = new ArrayList<>();
        for (int index = 0; index < order.size(); index += 2) {
            pairings.add(Pairing.of(pairings.size() + 1, seeded.get(order.get(index)), seeded.get(order.get(index + 1))));
        }
        return List.copyOf(pairings);
    }

    /** The next round: the winners of neighboring matches, in order. Empty after the final. */
    public static List<Pairing> nextRound(Round finished) {
        List<UUID> winners = new ArrayList<>();
        for (Pairing pairing : finished.pairings()) {
            MatchResult result = pairing.result();
            if (result == null || result.isDraw()) {
                throw new IllegalStateException("An elimination match needs a winner at table " + pairing.table());
            }
            winners.add(result.firstWon() ? pairing.a() : pairing.b());
        }
        if (winners.size() < 2) {
            return List.of();
        }
        List<Pairing> pairings = new ArrayList<>();
        for (int index = 0; index + 1 < winners.size(); index += 2) {
            pairings.add(Pairing.of(pairings.size() + 1, winners.get(index), winners.get(index + 1)));
        }
        return List.copyOf(pairings);
    }

    /** Seeds, from zero, in the order they are placed round the bracket: 1v8, 4v5, 2v7, 3v6. */
    static List<Integer> bracketOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(0, 1));
        while (order.size() < size) {
            int doubled = order.size() * 2;
            List<Integer> next = new ArrayList<>();
            for (int seed : order) {
                next.add(seed);
                next.add(doubled - 1 - seed);
            }
            order = next;
        }
        return List.copyOf(order);
    }
}
