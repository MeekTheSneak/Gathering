package dev.gathering.core.booster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything needed to open one kind of pack, as data.
 *
 * <p>The whole of what makes collation work with no code per set: the sheets a set prints on
 * and the arrangements its packs come out in. A set published today and a set published in
 * ten years are the same shape here, and neither needs a line written for it.
 *
 * <p>Nothing about any real set is written down in this module. What lives here is the shape;
 * what fills it comes from published collation data, and a set with none falls back to
 * whatever rarity odds the server configured. The rule is not a preference: booster structure
 * remembered rather than read is booster structure that is quietly wrong, and a pack that is
 * quietly wrong is an economy that is quietly wrong.
 *
 * @param setCode  the set these packs are of, lower case as Scryfall writes it
 * @param kind     which product of that set - "draft", "collector", "set" - so one set can
 *                 have several kinds of pack without them being different sets
 */
public record BoosterConfig(
        String setCode, String kind, Map<String, BoosterSheet> sheets, List<BoosterVariant> variants) {

    public BoosterConfig {
        setCode = setCode == null ? "" : setCode.trim().toLowerCase(java.util.Locale.ROOT);
        kind = kind == null || kind.isBlank() ? "default" : kind.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, BoosterSheet> keptSheets = new LinkedHashMap<>();
        if (sheets != null) {
            sheets.forEach((name, sheet) -> {
                if (name != null && sheet != null && !sheet.isEmpty()) {
                    keptSheets.put(name, sheet);
                }
            });
        }
        // Not Map.copyOf, for the same reason the sheets themselves are not: an order that
        // changes between launches is an opening that changes between launches.
        sheets = java.util.Collections.unmodifiableMap(keptSheets);

        List<BoosterVariant> keptVariants = new ArrayList<>();
        if (variants != null) {
            for (BoosterVariant variant : variants) {
                // A variant with no weight never turns up and a variant with no slots is not
                // a pack. Both are dropped here rather than guarded against at every use.
                if (variant != null && variant.weight() > 0 && !variant.isEmpty()) {
                    keptVariants.add(variant);
                }
            }
        }
        variants = List.copyOf(keptVariants);
    }

    /** Whether there is enough here to open anything at all. */
    public boolean isUsable() {
        return !variants.isEmpty() && !sheets.isEmpty() && whatIsMissing().isEmpty();
    }

    /**
     * The sheets the variants ask for and this config does not have.
     *
     * <p>Reported rather than thrown, because collation data arriving with a sheet missing is
     * a thing that happens to real data feeds - and the right answer is to fall back to the
     * configured odds for that set, which the caller can only decide if it is told.
     */
    public List<String> whatIsMissing() {
        List<String> missing = new ArrayList<>();
        for (BoosterVariant variant : variants) {
            for (String sheet : variant.slots().keySet()) {
                if (!sheets.containsKey(sheet) && !missing.contains(sheet)) {
                    missing.add(sheet);
                }
            }
        }
        return List.copyOf(missing);
    }

    /** The sum of every arrangement's weight, which is what a variant roll is taken out of. */
    public long totalWeight() {
        long total = 0;
        for (BoosterVariant variant : variants) {
            total += variant.weight();
        }
        return total;
    }

    /** The arrangement at this point in the weight, in the order the data was written. */
    public BoosterVariant variantAt(long at) {
        long seen = 0;
        for (BoosterVariant variant : variants) {
            seen += variant.weight();
            if (at < seen) {
                return variant;
            }
        }
        throw new IllegalArgumentException("Past the end of " + setCode + " " + kind + ": " + at);
    }

    /** What to call this kind of pack when two of them have to be told apart. */
    public String id() {
        return setCode + ":" + kind;
    }
}
