package dev.gathering.core.sealed;

import java.util.List;
import java.util.Locale;

/**
 * How good a chest is.
 *
 * <p>An end city and a village chest are not the same find, and a pack that comes out of one
 * should not be the same pack. What changes is which kind of booster is plausible: an ordinary
 * chest turns up an ordinary pack, and a collector booster is something you get out of the
 * places people build expeditions around.
 *
 * <p>Which chest is which is a rule rather than a setting. A server owner picks whether packs
 * come out of chests at all; they do not pick whether an end city is a better find than a
 * village, any more than they pick where diamonds are.
 *
 * <p>Pure.
 */
public enum LootRichness {

    /** Most chests. A pack here is a pack. */
    PLAIN,

    /**
     * The chests at the end of something.
     *
     * <p>Everything on this list is behind a boss, a dimension, a raid, or a dive - a place
     * somebody went to on purpose. That is what makes a collector booster out of one feel
     * earned rather than random.
     */
    RICH;

    /** The paths, under {@code minecraft:}, that count as a find worth making. */
    private static final List<String> RICH_PATHS = List.of(
            "chests/end_city_treasure",
            "chests/bastion_treasure",
            "chests/bastion_other",
            "chests/bastion_bridge",
            "chests/bastion_hoglin_stable",
            "chests/nether_bridge",
            "chests/stronghold_library",
            "chests/ancient_city",
            "chests/ancient_city_ice_box",
            "chests/woodland_mansion",
            "chests/buried_treasure",
            "chests/underwater_ruin_big",
            "chests/shipwreck_treasure",
            "chests/pillager_outpost",
            "chests/trial_chambers/reward_ominous",
            "chests/trial_chambers/reward_ominous_rare",
            "chests/trial_chambers/reward_ominous_unique",
            "archaeology/trail_ruins_rare");

    /**
     * How good the chest behind one loot table is.
     *
     * <p>Matched on Minecraft's own names, so a table this does not know is plain rather than
     * a failure. A new chest added by a later version is an ordinary find until somebody says
     * otherwise, which is the safe direction to be wrong in.
     */
    public static LootRichness of(String tableId) {
        String id = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
        if (!id.startsWith("minecraft:")) {
            return PLAIN;
        }
        String path = id.substring("minecraft:".length());
        return RICH_PATHS.contains(path) ? RICH : PLAIN;
    }

    public boolean isRich() {
        return this == RICH;
    }
}
