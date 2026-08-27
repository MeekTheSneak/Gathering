package dev.gathering.core.booster;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a server's acquisition paths do and do not reach.
 *
 * @param catalog how many printings the server says are obtainable at all
 * @param uncovered the ones no path reaches, in catalog order so two runs read the same
 * @param byFaucet  how many of the catalog each path reaches, so an admin can see which
 *                  product is carrying the coverage and which is carrying none
 */
public record CoverageReport(int catalog, Set<UUID> uncovered, Map<String, Integer> byFaucet) {

    public CoverageReport {
        uncovered = uncovered == null
                ? Set.of()
                : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(uncovered));
        byFaucet = byFaucet == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byFaucet));
    }

    public int covered() {
        return catalog - uncovered.size();
    }

    /** Whether every card the server says is obtainable actually is. */
    public boolean isComplete() {
        return uncovered.isEmpty();
    }

    /**
     * How much of the catalog is reachable, as a fraction.
     *
     * <p>One for an empty catalog: a server that says nothing is obtainable has covered
     * everything it asked for, and reporting nought would send an admin looking for a
     * hole that is not there.
     */
    public double fraction() {
        return catalog <= 0 ? 1.0d : covered() / (double) catalog;
    }

    /** Paths that reach nothing at all, which is nearly always a misconfiguration. */
    public List<String> reachingNothing() {
        List<String> idle = new java.util.ArrayList<>();
        byFaucet.forEach((name, reached) -> {
            if (reached == 0) {
                idle.add(name);
            }
        });
        return List.copyOf(idle);
    }
}
