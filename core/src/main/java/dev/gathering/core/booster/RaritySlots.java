package dev.gathering.core.booster;

import dev.gathering.core.card.Rarity;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a pack holds when nobody has published how the set was actually collated.
 *
 * <p>The honest approximation, and named as one. Real collation is print sheets; this is the
 * shape people describe packs with - so many commons, so many uncommons, a rare that is
 * sometimes a mythic - which was never quite how packs worked and has not been close for
 * years. It exists because a set with no published data still has to open, and a server that
 * refused would have holes in its catalog rather than approximate packs.
 *
 * <p>Configurable, because "eleven commons" is a fact about one era of one game and a server
 * running an older block, a cube, or a set of its own wants different numbers. Nothing here
 * is a constant anybody would have to edit code to change.
 *
 * @param slots       how many of each rarity a pack holds
 * @param upgradeFrom the slot that is sometimes something better - the rare slot
 * @param upgradeTo   what it becomes - the mythic
 * @param upgradeOneIn how rarely: one pack in this many. One or less means never, which is
 *                     what a set printed before mythics existed wants.
 */
public record RaritySlots(
        Map<Rarity, Integer> slots, Rarity upgradeFrom, Rarity upgradeTo, int upgradeOneIn) {

    /**
     * What a modern pack looks like from the outside, as a starting point rather than a rule.
     *
     * <p>Ten commons, three uncommons and a rare that is a mythic one time in eight. Written
     * here as a default a server can change, not as knowledge about any particular set - the
     * moment this is treated as what a set contains it has become the hardcoded booster
     * structure the whole design refuses.
     */
    public static RaritySlots usual() {
        Map<Rarity, Integer> slots = new EnumMap<>(Rarity.class);
        slots.put(Rarity.COMMON, 10);
        slots.put(Rarity.UNCOMMON, 3);
        slots.put(Rarity.RARE, 1);
        return new RaritySlots(slots, Rarity.RARE, Rarity.MYTHIC, 8);
    }

    public RaritySlots {
        Map<Rarity, Integer> kept = new EnumMap<>(Rarity.class);
        if (slots != null) {
            slots.forEach((rarity, count) -> {
                if (rarity != null && count != null && count > 0) {
                    kept.put(rarity, count);
                }
            });
        }
        slots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(kept));
        upgradeOneIn = Math.max(0, upgradeOneIn);
    }

    /** Whether a slot is ever upgraded at all. */
    public boolean upgrades() {
        return upgradeOneIn > 1
                && upgradeFrom != null
                && upgradeTo != null
                && upgradeFrom != upgradeTo
                && slots.getOrDefault(upgradeFrom, 0) > 0;
    }

    /** How many cards a pack comes to. */
    public int cards() {
        int cards = 0;
        for (int count : slots.values()) {
            cards += count;
        }
        return cards;
    }

}
