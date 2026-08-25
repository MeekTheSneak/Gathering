package dev.gathering.core.sealed;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which kind of booster comes out of a chest.
 *
 * <p>Every kind a set really sold can turn up, and they are not equally likely. A collector
 * booster is the one worth finding, so it is the one you rarely find - and it is much more
 * likely out of the chests people build expeditions around than out of a village barrel,
 * which is the whole of what makes where you looked matter.
 *
 * <p>The weights are a starting point rather than a balance pass, written down in one place
 * so they can be argued about. What they are not is a setting: a server owner picks which
 * sets and which chests, not whether a collector booster is rarer than a draft booster.
 *
 * <p>Pure.
 */
public final class BoosterOdds {

    /** What an ordinary booster weighs - draft, set, play, and anything unrecognised. */
    public static final int ORDINARY = 24;

    /** A collector booster out of a village chest. */
    public static final int COLLECTOR = 1;

    /** And out of an end city, a bastion, an ancient city. */
    public static final int COLLECTOR_WHERE_IT_IS_EARNED = 8;

    /**
     * A sample pack: a collector booster's four best slots and none of the rest.
     *
     * <p>Between the two, because that is what it is.
     */
    public static final int SAMPLE = 4;

    private BoosterOdds() {
    }

    /** Whether a kind of booster is the rare one. */
    private static boolean isCollector(String kind) {
        String named = named(kind);
        return named.equals("collector");
    }

    /** Whether a kind is a sample of the rare one. */
    private static boolean isSample(String kind) {
        return named(kind).equals("collector-sample");
    }

    /**
     * How likely one kind of booster is, out of a chest this good.
     *
     * @param kind      a booster kind as the published data names it
     * @param richness  how good the chest is
     */
    public static int weightOf(String kind, LootRichness richness) {
        if (isCollector(kind)) {
            return richness != null && richness.isRich()
                    ? COLLECTOR_WHERE_IT_IS_EARNED
                    : COLLECTOR;
        }
        if (isSample(kind)) {
            return SAMPLE;
        }
        return ORDINARY;
    }

    /**
     * The weights for every kind on offer, in the order they were offered.
     *
     * <p>Kept in order because the choosing walks it: a per-launch hash order would make the
     * same seed pick a different pack on a different day, and an economy nobody can check is
     * an economy nobody can audit.
     */
    public static Map<String, Integer> weightsFor(
            Collection<String> kinds, LootRichness richness) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (kinds == null) {
            return weights;
        }
        for (String kind : kinds) {
            if (kind != null && !kind.isBlank()) {
                weights.put(kind, weightOf(kind, richness));
            }
        }
        return weights;
    }

    /** Everything the weights add up to, which is what a roll is taken out of. */
    public static int totalOf(Map<String, Integer> weights) {
        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        return total;
    }

    /**
     * The kind a roll landed on.
     *
     * <p>Walked in order rather than sampled, so the same roll against the same offer is the
     * same pack every time.
     *
     * @param roll a number from zero up to, but not including, {@link #totalOf}
     * @return the kind, or null where there was nothing to choose from
     */
    public static String pick(Map<String, Integer> weights, int roll) {
        if (weights == null || weights.isEmpty()) {
            return null;
        }
        int left = Math.max(0, roll);
        String last = null;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            last = entry.getKey();
            left -= entry.getValue();
            if (left < 0) {
                return entry.getKey();
            }
        }
        // Past the end, which means a roll wider than the total. The last kind rather than
        // nothing: a pack that failed to come out because of arithmetic is worse than a
        // slightly likelier draft booster.
        return last;
    }

    private static String named(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }
}
