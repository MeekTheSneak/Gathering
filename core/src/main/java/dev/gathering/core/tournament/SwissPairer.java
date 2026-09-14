package dev.gathering.core.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who plays whom in a Swiss round.
 * <p>Round one folds the field by seed: the top half is paired against the bottom half, first
 * seed against the first seed of the bottom half. That is also the rule that makes losing on
 * purpose to lower a seed backfire - a player who drags their rating down is met by a top seed.
 * From round two players are paired with others on the same record, best first, and never with
 * somebody they have already played while any other pairing is possible.
 * <p>With an odd number still in, the lowest-ranked player who has not had a bye gets one.
 * <p>Pure and deterministic: the same field and history always pair the same way.
 */
public final class SwissPairer {

    /** How hard to look for a pairing with no rematch before allowing one. */
    private static final int MOST_ATTEMPTS = 200_000;

    private SwissPairer() {
    }

    /**
     * Pairs the next Swiss round.
     *
     * @param players  everybody still in, dropped players excluded by the caller
     * @param previous every round played so far
     * @param round    the number of the round being paired, from one
     * @return the pairings, tables numbered from one with the best-placed players at table one,
     *         and a bye last if there is one
     */
    public static List<Pairing> pair(List<Entrant> players, List<Round> previous, int round) {
        List<Entrant> order = new ArrayList<>(players);
        if (round <= 1 || previous.isEmpty()) {
            order.sort(Comparator.comparingDouble(Entrant::seed).reversed().thenComparing(Entrant::id));
        } else {
            List<UUID> ranked = Standings.of(players, previous).stream().map(row -> row.player().id()).toList();
            order.sort(Comparator.comparingInt(entrant -> ranked.indexOf(entrant.id())));
        }

        Entrant bye = null;
        if (order.size() % 2 == 1) {
            Set<UUID> hadABye = new HashSet<>();
            for (Round played : previous) {
                for (Pairing pairing : played.pairings()) {
                    if (pairing.isBye()) {
                        hadABye.add(pairing.a());
                    }
                }
            }
            for (int index = order.size() - 1; index >= 0; index--) {
                if (!hadABye.contains(order.get(index).id())) {
                    bye = order.remove(index);
                    break;
                }
            }
            if (bye == null) {
                bye = order.remove(order.size() - 1);
            }
        }

        List<UUID[]> pairs;
        if (round <= 1 || previous.isEmpty()) {
            pairs = new ArrayList<>();
            int half = order.size() / 2;
            for (int index = 0; index < half; index++) {
                pairs.add(new UUID[] {order.get(index).id(), order.get(index + half).id()});
            }
        } else {
            Set<String> played = playedPairs(previous);
            List<UUID> ids = order.stream().map(Entrant::id).toList();
            pairs = pairAvoiding(ids, played);
            if (pairs == null) {
                // Everybody has played everybody they could: rematches are unavoidable, and
                // the field is simply paired top down.
                pairs = new ArrayList<>();
                for (int index = 0; index + 1 < ids.size(); index += 2) {
                    pairs.add(new UUID[] {ids.get(index), ids.get(index + 1)});
                }
            }
        }

        List<Pairing> pairings = new ArrayList<>();
        for (int index = 0; index < pairs.size(); index++) {
            pairings.add(Pairing.of(index + 1, pairs.get(index)[0], pairs.get(index)[1]));
        }
        if (bye != null) {
            pairings.add(Pairing.of(0, bye.id(), null));
        }
        return List.copyOf(pairings);
    }

    static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static Set<String> playedPairs(List<Round> previous) {
        Set<String> played = new HashSet<>();
        for (Round round : previous) {
            for (Pairing pairing : round.pairings()) {
                if (!pairing.isBye()) {
                    played.add(key(pairing.a(), pairing.b()));
                }
            }
        }
        return played;
    }

    /** Pairs top down, each player with the best-placed opponent they have not played, backtracking. */
    private static List<UUID[]> pairAvoiding(List<UUID> ids, Set<String> played) {
        List<UUID[]> pairs = new ArrayList<>();
        int[] attempts = {0};
        return pairFrom(new ArrayList<>(ids), played, pairs, attempts) ? pairs : null;
    }

    private static boolean pairFrom(List<UUID> left, Set<String> played, List<UUID[]> pairs, int[] attempts) {
        if (left.isEmpty()) {
            return true;
        }
        if (++attempts[0] > MOST_ATTEMPTS) {
            return false;
        }
        UUID first = left.get(0);
        for (int index = 1; index < left.size(); index++) {
            UUID candidate = left.get(index);
            if (played.contains(key(first, candidate))) {
                continue;
            }
            List<UUID> rest = new ArrayList<>(left);
            rest.remove(index);
            rest.remove(0);
            pairs.add(new UUID[] {first, candidate});
            if (pairFrom(rest, played, pairs, attempts)) {
                return true;
            }
            pairs.remove(pairs.size() - 1);
        }
        return false;
    }
}
