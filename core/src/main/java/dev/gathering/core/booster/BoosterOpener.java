package dev.gathering.core.booster;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.DeterministicRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opens a pack: one interpreter, every set, no code that knows which set it is.
 * <p>Two rolls and no cleverness. Pick the arrangement the pack came out as, weighted against
 * the set's other arrangements; then fill each of its slots off the named sheet, weighted by
 * how often each card appears on it. Everything real collation does - wildcard slots, sheets
 * that mix rarities, list slots, foil sheets, variants that only turn up one pack in eight -
 * is one of those two weights, which is why none of it needs a line of code.
 * <p>Seeded from the same deterministic stream the table shuffles libraries with, so a pack
 * is reproducible from what opened it. That matters for more than tests: an economy where
 * nobody can check what a pack should have contained is an economy nobody can audit.
 * <p>Pure. Nothing here reaches a network, a file or a registry.
 */
public final class BoosterOpener {

    private BoosterOpener() {
    }

    /**
     * Opens one pack.
     *
     * @param seed  the stream this pack is drawn from; a different seed is a different pack
     * @param label what to name this opening within that stream, so two packs opened from one
     *              seed are not the same pack
     * @throws IllegalArgumentException if the config cannot open anything, which the caller
     *                                  should have asked {@link BoosterConfig#isUsable} first
     */
    public static OpenedPack open(BoosterConfig config, byte[] seed, String label) {
        if (config == null || !config.isUsable()) {
            throw new IllegalArgumentException(
                    "Nothing to open: " + (config == null ? "no config" : config.id()
                            + " is missing " + config.whatIsMissing()));
        }
        DeterministicRandom rolls = DeterministicRandom.forLabel(seed, "booster:" + label);

        BoosterVariant variant = config.variantAt(roll(rolls, config.totalWeight()));

        List<CardIdentity> cards = new ArrayList<>(variant.cards());
        for (Map.Entry<String, Integer> slot : variant.slots().entrySet()) {
            BoosterSheet sheet = config.sheets().get(slot.getKey());
            fill(cards, sheet, slot.getValue(), rolls);
        }
        return new OpenedPack(config.id(), cards);
    }

    /**
     * Takes this many cards off one sheet.
     * <p>A fixed sheet is not taken from, it is copied out whole; see {@link BoosterSheet#fixed}.
     * <p>Otherwise: a sheet that refuses duplicates has each card removed as it is taken, which is what
     * cutting a real sheet does: the same card cannot be in one pack twice because there is
     * only one of it in that column. Taking from a copy rather than from the sheet itself,
     * because a sheet is data and the next pack wants all of it back.
     */
    private static void fill(
            List<CardIdentity> into, BoosterSheet sheet, int howMany, DeterministicRandom rolls) {
        if (sheet.fixed()) {
            // Not drawn from: a fixed sheet is the pack. Every card on it, as many copies as
            // its weight, in the order the data was written - and no roll taken, so a pack
            // with one of these in it stays reproducible alongside packs without.
            int taken = 0;
            for (Map.Entry<UUID, Integer> card : sheet.weights().entrySet()) {
                for (int copy = 0; copy < card.getValue() && taken < howMany; copy++) {
                    into.add(sheet.identityOf(card.getKey()));
                    taken++;
                }
            }
            return;
        }
        BoosterSheet left = sheet;
        for (int card = 0; card < howMany; card++) {
            if (left.isEmpty()) {
                // A sheet too small to fill its own slot. Real data does not do this, but a
                // truncated feed does, and a short pack beats a pack that throws while
                // somebody is opening it.
                break;
            }
            UUID printing = left.at(roll(rolls, left.total()));
            into.add(sheet.identityOf(printing));
            if (!sheet.duplicates()) {
                left = left.without(List.of(printing));
            }
        }
    }

    /**
     * A number in {@code [0, total)}.
     * <p>Taken from the stream's own wide draw rather than assembled here. This once held a
     * hand-rolled high-and-low pair for totals past what an int holds, and it was quietly
     * biased: for a total just over the limit the high draw was always nought, so the upper
     * half of the sheet could never come up. It was then deleted on the reasoning that no
     * real sheet is that heavy - which real collation promptly disproved, since a foil sheet
     * states its odds as exact ratios running to hundreds of billions.
     * <p>So the wide case is real, and it belongs where every other unbiased draw in the mod
     * is made: in the stream, rejection-sampled, checked once.
     */
    private static long roll(DeterministicRandom rolls, long total) {
        if (total <= 0) {
            throw new IllegalArgumentException("Nothing sensible to draw from: " + total);
        }
        return rolls.nextLong(total);
    }
}
