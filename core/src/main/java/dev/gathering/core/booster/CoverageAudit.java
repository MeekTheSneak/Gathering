package dev.gathering.core.booster;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which cards a server's own configuration can never produce.
 *
 * <p>Completeness guaranteed rather than intended. A server declares what it considers
 * obtainable and declares the paths cards come out of, and this says whether the second
 * covers the first - which is a computation, and so cannot drift the way a promise can.
 *
 * <p>It takes the server's actual configuration rather than the mod's defaults, because the
 * question is about this server. An admin who removed the stall, narrowed the catalogue, or
 * added a product of their own gets an answer about the game their players are playing.
 *
 * <p>What it does with the remainder is the point: the uncovered cards become the Archive
 * Pack's sheet, so the long tail is reachable through play and the archive shrinks by
 * computation as products are added. Nothing has to be curated and nothing goes stale.
 *
 * <p>Pure, and cheap enough to run in a test suite over a whole catalogue.
 */
public final class CoverageAudit {

    private CoverageAudit() {
    }

    /**
     * @param catalogue every printing the server says a player may obtain
     * @param faucets   every path a card can come out of
     */
    public static CoverageReport of(Collection<UUID> catalogue, Collection<? extends Faucet> faucets) {
        Set<UUID> wanted = new LinkedHashSet<>();
        if (catalogue != null) {
            for (UUID printing : catalogue) {
                if (printing != null) {
                    wanted.add(printing);
                }
            }
        }

        Map<String, Integer> byFaucet = new LinkedHashMap<>();
        Set<UUID> uncovered = new LinkedHashSet<>(wanted);
        if (faucets != null) {
            for (Faucet faucet : faucets) {
                if (faucet == null) {
                    continue;
                }
                Set<UUID> reaches = faucet.reaches();
                int within = 0;
                if (reaches != null) {
                    for (UUID printing : reaches) {
                        // Only what the catalogue asked about. A path that also produces
                        // cards the server has excluded is not thereby covering them - the
                        // exclusion was a deliberate visible choice and this must not quietly
                        // report it as satisfied.
                        if (wanted.contains(printing)) {
                            within++;
                            uncovered.remove(printing);
                        }
                    }
                }
                // Summed rather than replaced: two paths may share a name, and an admin who
                // named two products the same wants the total, not whichever was read last.
                byFaucet.merge(faucet.name(), within, Integer::sum);
            }
        }
        return new CoverageReport(wanted.size(), uncovered, byFaucet);
    }

    /**
     * The Archive Pack's sheet: exactly what nothing else reaches.
     *
     * <p>Every card on it once, because the archive is a safety net rather than a product
     * with a rarity structure - and weighting the long tail would mean deciding which
     * forgotten promo is the chase card, which is not a decision anybody should be making.
     *
     * <p>Empty when the configuration already covers everything, which is the state a server
     * is trying to reach: at that point the archive has nothing to hold and the item that
     * draws from it has nothing to give, and both of those are correct.
     */
    public static BoosterSheet archiveSheet(CoverageReport report) {
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        if (report != null) {
            for (UUID printing : report.uncovered()) {
                weights.put(printing, 1);
            }
        }
        return new BoosterSheet("archive", false, false, weights);
    }
}
