package dev.gathering.core.booster;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One arrangement a pack can come out as: how many cards off which sheets.
 *
 * <p>A set does not have one pack layout, it has several with different chances - the plain
 * one, the one with a rare-slot upgrade, the one with a special card in the land slot. Which
 * you got was decided when the sheet was cut, so it is chosen first here and everything else
 * follows from it.
 *
 * @param weight  how often this arrangement turns up against the set's other arrangements
 * @param slots   how many cards to take from each sheet, by sheet name
 */
public record BoosterVariant(String name, int weight, Map<String, Integer> slots) {

    public BoosterVariant {
        name = name == null ? "" : name;
        if (weight < 0) {
            throw new IllegalArgumentException("A booster variant cannot appear negatively often");
        }
        Map<String, Integer> kept = new LinkedHashMap<>();
        if (slots != null) {
            slots.forEach((sheet, count) -> {
                if (sheet != null && !sheet.isBlank() && count != null && count > 0) {
                    kept.put(sheet, count);
                }
            });
        }
        slots = Map.copyOf(kept);
    }

    /** How many cards this arrangement comes to, which is what a pack's size means. */
    public int cards() {
        int cards = 0;
        for (int count : slots.values()) {
            cards += count;
        }
        return cards;
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }
}
