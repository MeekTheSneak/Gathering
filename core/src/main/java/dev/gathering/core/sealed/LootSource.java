package dev.gathering.core.sealed;

import java.util.Locale;
import java.util.Optional;

/**
 * Where in the world a sealed pack can turn up, and how often.
 * <p>One of the two ways into a collection - the other is buying it - and the one that makes
 * a server's early game feel like a card game rather than a shop. A pack in a dungeon chest
 * is a small event; a pack you can buy whenever you like is inventory.
 * <p>Which of these a server uses is its own choice, made in the config file it already has.
 * What is here is the mapping from a loot table to a source and the odds each source runs at,
 * because both of those are rules rather than settings: a server owner picks whether their
 * players fish packs out of the sea, not which of Minecraft's tables counts as fishing.
 * <p>The odds are a starting point rather than a balance pass. They are written down here in
 * one place so they can be argued about, and they are deliberately long: a pack that turns up
 * in one chest in eight is a thing you remember, and one that turns up in every chest is a
 * currency.
 * <p>Pure.
 */
public enum LootSource {

    /** Treasure pulled out of the water, which is already where the interesting things are. */
    FISHING("fishing", 12),

    /** Anything in a chest somebody built a structure around. */
    STRUCTURES("structures", 8),

    /** Brushed out of the ground, which is the slowest of the three by a distance. */
    DIGGING("archaeology", 20);

    private final String configName;
    private final int oneIn;

    LootSource(String configName, int oneIn) {
        this.configName = configName;
        this.oneIn = oneIn;
    }

    /** What a server config calls this source in its list. */
    public String configName() {
        return configName;
    }

    /** How rare a pack is here: one in this many of these tables rolls one. */
    public int oneIn() {
        return oneIn;
    }

    /**
     * Which source a loot table belongs to, if any.
     * <p>Matched on Minecraft's own table names rather than on a list of every chest in the
     * game, so a table added by a later version is covered without anybody noticing it was
     * not. Deliberately not matched on other mods' tables: a pack falling out of somebody
     * else's dungeon is a surprise nobody asked this mod for.
     *
     * @param tableId a loot table's full id, namespace and all
     */
    public static Optional<LootSource> of(String tableId) {
        String id = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
        if (!id.startsWith("minecraft:")) {
            return Optional.empty();
        }
        String path = id.substring("minecraft:".length());
        if (path.equals("gameplay/fishing/treasure")) {
            return Optional.of(FISHING);
        }
        if (path.startsWith("archaeology/")) {
            return Optional.of(DIGGING);
        }
        if (path.startsWith("chests/")) {
            return Optional.of(STRUCTURES);
        }
        return Optional.empty();
    }

    /** The source a config file named, if it named one this knows. */
    public static Optional<LootSource> named(String configName) {
        String wanted = configName == null ? "" : configName.trim().toLowerCase(Locale.ROOT);
        for (LootSource source : values()) {
            if (source.configName.equals(wanted)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
