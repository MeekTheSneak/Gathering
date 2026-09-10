package dev.gathering.core.booster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which color a themed pack is, worked out from what is on its sheets.
 * <p>Jumpstart sells one color at a time. A pack called Goblins is a red pack, one called
 * Wizards is a blue one, and a player picking a color is picking from that color's packs -
 * which is the whole shape of the product and the reason it is the right thing to hand
 * somebody who has just learned the controls.
 * <p>Nowhere in the collation does it say so. What the file has is sheets of printings and
 * their colors, which is enough: the color of a pack is the color most of its colored
 * cards are. So this counts rather than being told, and a set that names its packs something
 * else entirely still sorts correctly.
 * <p>Lands and anything else colorless are not counted. A pack is not less red for running
 * seven Mountains, and counting them would make every pack in every set look colorless.
 * <p>Pure. It reads a {@link BoosterConfig} and nothing else.
 */
public final class BoosterColors {

    /** The letters, in the order Magic writes them. */
    public static final String WUBRG = "WUBRG";

    private BoosterColors() {
    }

    /**
     * How much of each color one arrangement draws.
     * <p>Weighted by the sheet's own weights rather than by how many distinct cards are on it:
     * a sheet where one white card is nine times as likely as the rest is a white sheet, and
     * counting distinct printings would call it something else.
     */
    public static Map<Character, Long> colorWeight(BoosterConfig config, BoosterVariant variant) {
        Map<Character, Long> counted = new LinkedHashMap<>();
        if (config == null || variant == null) {
            return counted;
        }
        for (Map.Entry<String, Integer> slot : variant.slots().entrySet()) {
            BoosterSheet sheet = config.sheets().get(slot.getKey());
            if (sheet == null) {
                continue;
            }
            for (UUID printing : sheet.printings()) {
                String colors = sheet.colorOf(printing);
                if (colors == null || colors.isEmpty()) {
                    // Colorless, which is almost always a land. A pack is not less red for
                    // running seven Mountains.
                    continue;
                }
                for (int at = 0; at < colors.length(); at++) {
                    counted.merge(colors.charAt(at), 1L, Long::sum);
                }
            }
        }
        return counted;
    }

    /**
     * The one color this arrangement is, or nothing for a pack with no color to speak of.
     * <p>"Most of it" rather than "all of it", because a mono-colored Jumpstart pack still
     * carries the odd splash and a rule of "every card" would answer nothing for every real
     * pack. Ties go to the earlier letter in WUBRG, so the answer does not depend on which
     * order a map happened to iterate in.
     */
    public static Character colorOf(BoosterConfig config, BoosterVariant variant) {
        Map<Character, Long> counted = colorWeight(config, variant);
        Character best = null;
        long most = 0;
        for (int at = 0; at < WUBRG.length(); at++) {
            char color = WUBRG.charAt(at);
            long many = counted.getOrDefault(color, 0L);
            if (many > most) {
                most = many;
                best = color;
            }
        }
        return best;
    }

    /**
     * Every arrangement in this pack kind that is mostly that color.
     * <p>In the order the collation published them, so a server picking one at random from a
     * seeded source gets the same answer twice.
     */
    public static List<BoosterVariant> inColor(BoosterConfig config, char color) {
        List<BoosterVariant> found = new ArrayList<>();
        if (config == null) {
            return found;
        }
        char wanted = Character.toUpperCase(color);
        for (BoosterVariant variant : config.variants()) {
            Character is = colorOf(config, variant);
            if (is != null && is == wanted) {
                found.add(variant);
            }
        }
        return List.copyOf(found);
    }

    /**
     * How many arrangements each color has, for a server checking a set is worth offering.
     * <p>A set with nothing in one color cannot answer a player who picks it, and finding
     * that out when they have already chosen is worse than not offering it.
     */
    public static Map<Character, Integer> countByColor(BoosterConfig config) {
        Map<Character, Integer> counted = new LinkedHashMap<>();
        for (int at = 0; at < WUBRG.length(); at++) {
            counted.put(WUBRG.charAt(at), 0);
        }
        if (config == null) {
            return counted;
        }
        for (BoosterVariant variant : config.variants()) {
            Character is = colorOf(config, variant);
            if (is != null) {
                counted.merge(is, 1, Integer::sum);
            }
        }
        return counted;
    }

    /** Whether every one of the five colors has at least one pack here. */
    public static boolean coversEveryColor(BoosterConfig config) {
        return countByColor(config).values().stream().allMatch(many -> many > 0);
    }
}
