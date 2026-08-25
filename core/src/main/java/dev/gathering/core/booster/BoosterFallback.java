package dev.gathering.core.booster;

import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A pack for a set nobody has published the collation of.
 *
 * <p>Built as sheets and arrangements for the one interpreter rather than as a second way of
 * opening a pack. That is the whole of the design here: there is one opener, and this only
 * decides what to hand it. A separate fallback opener would be a second implementation of
 * duplicates, foils, weights and short sheets, and the two would agree until the day one of
 * them was fixed.
 *
 * <p>What it produces is deliberately plain - one sheet per rarity, every card on it once, so
 * every common is as likely as every other common. That is not how any real set was printed
 * and it is not pretending to be: it is what "we do not know how this set was collated"
 * honestly comes to.
 *
 * <p>The sometimes-a-mythic rare slot is an arrangement rather than a special case, because
 * that is exactly what an arrangement is: one pack in eight comes out with a different set of
 * slots. Written as a weight, it needs no code in the opener at all.
 *
 * <p>Pure.
 */
public final class BoosterFallback {

    private BoosterFallback() {
    }

    /**
     * Sheets and arrangements for a set, from what is in it and how packs are configured.
     *
     * @param pool  the printings in the set, by rarity - what a server's catalogue holds
     * @return a config the interpreter can open, or one that reports itself unusable when the
     *         set has nothing to fill any of its slots with
     */
    public static BoosterConfig configFor(
            String setCode, String kind, Map<Rarity, List<UUID>> pool, RaritySlots odds) {
        if (pool == null || odds == null) {
            return new BoosterConfig(setCode, kind, Map.of(), List.of());
        }

        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        for (Map.Entry<Rarity, List<UUID>> entry : pool.entrySet()) {
            Rarity rarity = entry.getKey();
            List<UUID> printings = entry.getValue();
            if (rarity == null || printings == null || printings.isEmpty()) {
                continue;
            }
            Map<UUID, Integer> weights = new LinkedHashMap<>();
            for (UUID printing : printings) {
                // Once each. A card that appears twice in the catalogue is still one card in
                // the set, and counting it twice would make it quietly twice as common.
                weights.putIfAbsent(printing, 1);
            }
            sheets.put(nameOf(rarity), new BoosterSheet(nameOf(rarity), false, false, weights));
        }

        Map<String, Integer> plain = new LinkedHashMap<>();
        for (Map.Entry<Rarity, Integer> slot : odds.slots().entrySet()) {
            // A slot for a rarity this set has none of is dropped rather than left to fail.
            // A set with no mythics is ordinary; a pack that throws while somebody opens it
            // is not.
            if (sheets.containsKey(nameOf(slot.getKey()))) {
                plain.put(nameOf(slot.getKey()), slot.getValue());
            }
        }
        if (plain.isEmpty()) {
            return new BoosterConfig(setCode, kind, sheets, List.of());
        }

        List<BoosterVariant> variants = new ArrayList<>(2);
        boolean upgradeable = odds.upgrades()
                && sheets.containsKey(nameOf(odds.upgradeTo()))
                && plain.containsKey(nameOf(odds.upgradeFrom()));
        if (!upgradeable) {
            variants.add(new BoosterVariant("plain", 1, plain));
            return new BoosterConfig(setCode, kind, sheets, variants);
        }

        // One in eight, as two arrangements weighted seven and one. The opener knows nothing
        // about upgrades and does not need to.
        Map<String, Integer> upgraded = new LinkedHashMap<>(plain);
        String from = nameOf(odds.upgradeFrom());
        String to = nameOf(odds.upgradeTo());
        int downgraded = upgraded.get(from) - 1;
        if (downgraded > 0) {
            upgraded.put(from, downgraded);
        } else {
            upgraded.remove(from);
        }
        upgraded.merge(to, 1, Integer::sum);

        variants.add(new BoosterVariant("plain", odds.upgradeOneIn() - 1, plain));
        variants.add(new BoosterVariant("upgraded", 1, upgraded));
        return new BoosterConfig(setCode, kind, sheets, variants);
    }

    /**
     * The sheet a rarity's cards go on.
     *
     * <p>Named from the rarity rather than numbered, so a config built this way reads the same
     * as one that came from published data - which is what makes the two indistinguishable to
     * everything downstream, including the coverage auditor.
     */
    public static String nameOf(Rarity rarity) {
        return rarity == null ? Rarity.UNKNOWN.scryfallName() : rarity.scryfallName();
    }
}
