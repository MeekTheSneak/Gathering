package dev.gathering.core.sealed;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which kind of booster comes out of a chest.
 * <p>Every kind a set really sold can turn up, and they are nowhere near equally likely. The
 * ordinary booster - draft, set, play - is the pack the whole world is full of. The collector
 * booster, the box topper and the promo pack are the ones worth finding, so they are the ones
 * you almost never find out of a village barrel and stand a real chance at out of an end city,
 * which is the whole of what makes where you looked matter.
 * <p>The gap is wide on purpose and it got wider. When a pack turned up in one chest in eight,
 * a collector booster at one part in twenty-five of those was something a player saw in an
 * evening. A pack now turns up in about half of them ({@link LootSource}), so the same ratio
 * would have made the rare pack ordinary by volume alone - the two numbers have to move
 * together or "rare" stops meaning anything.
 * <p>Where it lands: out of an ordinary chest, under one pack in two hundred is a rare kind;
 * out of a chest at the end of something, about one in ten. Against an hour of play that is a
 * rare pack every four or five hours, and almost all of them out of somewhere somebody went on
 * purpose. {@code BoosterOddsTest} holds that band as a property.
 * <p>The weights are a balance decision written down in one place so it can be argued about.
 * What they are not is a setting: a server owner picks which sets and which chests, not
 * whether a collector booster is rarer than a draft booster.
 * <p>Pure.
 */
public final class BoosterOdds {

    /** What an ordinary booster weighs - draft, set, play, and anything unrecognized. */
    public static final int ORDINARY = 200;

    /** A collector booster, a box topper or a promo pack out of a village chest. */
    public static final int RARE = 1;

    /** And out of an end city, a bastion, an ancient city. */
    public static final int RARE_WHERE_IT_IS_EARNED = 25;

    /**
     * A sample pack: a collector booster's four best slots and none of the rest.
     * <p>Between the two, because that is what it is.
     */
    public static final int SAMPLE = 6;

    /** And the same, out of a chest worth an expedition. */
    public static final int SAMPLE_WHERE_IT_IS_EARNED = 30;

    /**
     * What makes a kind one of the rare ones, in the words the published data uses.
     * <p>Matched on the word inside the name rather than on the whole of it. MTGJSON names a
     * booster after what the box said, so the rare kinds arrive as {@code collector},
     * {@code box-topper}, {@code topper}, {@code promo}, {@code vip} and a dozen spellings
     * nobody can enumerate in advance - and the old check, an equals against the single string
     * "collector", quietly priced every one of the others as an ordinary draft booster.
     */
    private static final java.util.List<String> RARE_WORDS = java.util.List.of(
            "collector", "topper", "promo", "vip", "premium", "gift", "special");

    private BoosterOdds() {
    }

    /** Whether a kind of booster is one of the rare ones. */
    private static boolean isRare(String kind) {
        String named = named(kind);
        if (isSample(named)) {
            // A sample of a collector booster is not a collector booster, and the word is in
            // the name of both.
            return false;
        }
        for (String word : RARE_WORDS) {
            if (named.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a kind is a sample of the rare one. */
    private static boolean isSample(String kind) {
        return named(kind).contains("sample");
    }

    /**
     * How likely one kind of booster is, out of a chest this good.
     *
     * @param kind      a booster kind as the published data names it
     * @param richness  how good the chest is
     */
    public static int weightOf(String kind, LootRichness richness) {
        boolean earned = richness != null && richness.isRich();
        if (isRare(kind)) {
            return earned ? RARE_WHERE_IT_IS_EARNED : RARE;
        }
        if (isSample(kind)) {
            return earned ? SAMPLE_WHERE_IT_IS_EARNED : SAMPLE;
        }
        return ORDINARY;
    }

    /**
     * The weights for every kind on offer, in the order they were offered.
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
