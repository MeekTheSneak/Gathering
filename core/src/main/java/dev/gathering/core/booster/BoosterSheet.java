package dev.gathering.core.booster;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One sheet a pack draws from: printings, each with the weight it appears at.
 * <p>The unit real collation is expressed in. A print sheet is a physical thing - a large
 * sheet of card that gets cut up - and how often a card turns up in packs is how many times
 * it appears on the sheet. Weights are those counts, so a card printed twice on a sheet is
 * twice as likely as one printed once, and nothing here has to know what a rarity is.
 * <p>That is the whole reason this exists rather than a rarity table. Sets have not been
 * "eleven commons, three uncommons, one rare" for years: they have wildcard slots, list
 * slots, sheets that mix rarities, sheets that only appear in some booster variants. All of
 * it falls out of weighted sheets, and none of it needs code that knows which set it is.
 *
 * @param foil       whether cards drawn from this sheet come out foil, which is a property of
 *                   the sheet in the printing process and not of any card on it
 * @param duplicates whether one pack may take the same card off this sheet twice. Real sheets
 *                   are cut so a pack cannot, with a handful of documented exceptions, so
 *                   this is data rather than an assumption.
 * @param fixed      whether this sheet is not drawn from at all: every card on it goes into
 *                   the pack, as many copies as its weight. That is what a Jumpstart-style
 *                   themed pack is - a fixed list of cards printed as a unit - and it is a
 *                   property of the sheet rather than a second kind of slot, because the data
 *                   published for those products says so on the sheet.
 */
public record BoosterSheet(
        String name, boolean foil, boolean duplicates, boolean fixed, Map<UUID, Long> weights) {

    public BoosterSheet {
        name = name == null ? "" : name;
        Map<UUID, Long> kept = new LinkedHashMap<>();
        if (weights != null) {
            weights.forEach((printing, weight) -> {
                if (printing != null && weight != null && weight > 0) {
                    kept.put(printing, weight);
                }
            });
        }
        // Not Map.copyOf: its iteration order is unspecified and in practice comes out of a
        // hash salted once per launch, so the same seed would open a different pack every
        // time the game started - and a pack nobody can check afterwards is an economy
        // nobody can audit. The order data was written in is the order it is walked in.
        weights = java.util.Collections.unmodifiableMap(kept);

    }

    /** A sheet with nothing on it, for a server whose faucets already reach everything. */
    public static final BoosterSheet EMPTY =
            new BoosterSheet("", false, false, false, Map.of());

    public boolean isEmpty() {
        return weights.isEmpty();
    }

    /** How many distinct printings are on it, which is not how heavy it is. */
    public int size() {
        return weights.size();
    }

    /**
     * The sum of every weight, which is what a draw is taken out of.
     * <p>A long, and not out of caution: published collation writes a foil sheet's odds as
     * exact integer ratios, and a real one of those comes to two hundred billion. An int
     * total would be wrong for the sheets that need the precision most.
     * <p>One card's weight is a long for the same reason, and it was not always. Marvel's
     * Spider-Man puts a weight of 2,274,495,300 on one card of its foil sheet, which is past
     * what an int holds - so the whole set's collation was refused and every pack of it came
     * out of the fallback, opening at rarity odds rather than the real ones. Nothing said so
     * except one line in the log.
     */
    public long total() {
        long total = 0;
        for (long weight : weights.values()) {
            total += weight;
        }
        return total;
    }

    /**
     * The printing at this point in the sheet's weight.
     * <p>Walked in the map's own order, which is the order the data was written in, so the
     * same roll against the same sheet always lands on the same card.
     *
     * @param at a value in {@code [0, total())}
     */
    public UUID at(long at) {
        long seen = 0;
        for (Map.Entry<UUID, Long> entry : weights.entrySet()) {
            seen += entry.getValue();
            if (at < seen) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("Past the end of sheet " + name + ": " + at);
    }

    /** The same sheet with these printings taken off it, for a slot that refuses duplicates. */
    public BoosterSheet without(List<UUID> printings) {
        if (printings == null || printings.isEmpty()) {
            return this;
        }
        Map<UUID, Long> left = new LinkedHashMap<>(weights);
        for (UUID printing : printings) {
            left.remove(printing);
        }
        return left.size() == weights.size()
                ? this
                : new BoosterSheet(name, foil, duplicates, fixed, left);
    }

    /** What a card off this sheet is: a printing, and foil if the sheet is a foil sheet. */
    public CardIdentity identityOf(UUID printing) {
        return CardIdentity.ofPrinting(printing, foil);
    }

    /** Every printing on it, for the coverage auditor's benefit. */
    public List<UUID> printings() {
        return List.copyOf(new ArrayList<>(weights.keySet()));
    }
}
