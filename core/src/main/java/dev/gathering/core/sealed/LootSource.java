package dev.gathering.core.sealed;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where in the world a sealed pack can turn up, and how often.
 * <p>One of the two ways into a collection - the other is buying it - and the one that makes
 * a server's early game feel like a card game rather than a shop. What a player finds is
 * boosters; what a player buys is everything bigger, and the coin that buys it is the thing
 * that is actually rare. See {@link CoinDrops}.
 * <p>Which of these a server uses is its own choice, made in the config file it already has.
 * What is here is the mapping from a loot table to a source and the odds each source runs at,
 * because both of those are rules rather than settings: a server owner picks whether their
 * players fish packs out of the sea, not which of Minecraft's tables counts as fishing.
 * <p><b>An ordinary booster is common.</b> It was one chest in eight, one treasure catch in
 * twelve and one brushed block in twenty, and at those odds a player could explore a whole
 * evening and open nothing - which made the shop the only way to play the game the mod is
 * about, and made emeralds the game. A booster is a pack of commons: it should turn up in
 * about half the loot chests in the world, in almost all of the ones worth an expedition, and
 * now and then off a mob somebody killed. What stays rare is the collector booster inside
 * that roll ({@link BoosterOdds}) and the coin beside it.
 * <p>Roughly, for an hour of ordinary play - a village, a mineshaft and a shipwreck opened, a
 * couple of chests worth going to, and sixty-odd hostile mobs killed: about seven boosters,
 * one of them rare every four or five hours. {@code LootYieldTest} does that arithmetic
 * against these numbers so the sentence cannot quietly stop being true.
 * <p>Pure.
 */
public enum LootSource {

    /** Treasure pulled out of the water, which is already where the interesting things are. */
    FISHING("fishing", 4, 4),

    /**
     * Anything in a chest somebody built a structure around.
     * <p>The one source that changes with the chest. Half of the ordinary chests in the world
     * and every chest at the end of something: a player who went to an ancient city has earned
     * the pack as well as the odds on what kind it is.
     */
    STRUCTURES("structures", 2, 1),

    /** Brushed out of the ground, which is the slowest of these by a distance. */
    DIGGING("archaeology", 4, 2),

    /**
     * Off something that died fighting a player.
     * <p>Occasional on purpose: sixty kills is a busy hour of caving and about one pack. It is
     * the one source that can be automated, so it is also the one source that is deliberately
     * worth nothing on its own - a mob drops the ordinary booster and never a Mana Coin, so a
     * farm makes a pile of commons and buys exactly nothing with them.
     * <p>Only a kill a player had a hand in. Without that, a mob farm's drop chute is a faucet
     * nobody is playing, which is the thing this whole rework exists to stop being true.
     */
    MOBS("mobs", 150, 150);

    /**
     * The mobs a pack can come off, by the loot table each drops through.
     * <p>Named rather than matched on a prefix: {@code entities/} is every mob in the game, and
     * a booster out of a sheep is a joke that stops being funny on the second wool farm. The
     * four the world is built around are not here either - they drop the Archive Pack, which is
     * the rarer and better thing to get out of a boss. See {@link ArchiveDrops}.
     */
    private static final List<String> MOBS_WORTH_FIGHTING = List.of(
            "entities/blaze",
            "entities/bogged",
            "entities/breeze",
            "entities/cave_spider",
            "entities/creeper",
            "entities/drowned",
            "entities/enderman",
            "entities/endermite",
            "entities/evoker",
            "entities/ghast",
            "entities/giant",
            "entities/guardian",
            "entities/hoglin",
            "entities/husk",
            "entities/illusioner",
            "entities/magma_cube",
            "entities/phantom",
            "entities/piglin",
            "entities/piglin_brute",
            "entities/pillager",
            "entities/ravager",
            "entities/shulker",
            "entities/silverfish",
            "entities/skeleton",
            "entities/slime",
            "entities/spider",
            "entities/stray",
            "entities/vex",
            "entities/vindicator",
            "entities/witch",
            "entities/wither_skeleton",
            "entities/zoglin",
            "entities/zombie",
            "entities/zombie_villager",
            "entities/zombified_piglin");

    private final String configName;
    private final int oneIn;
    private final int oneInRich;

    LootSource(String configName, int oneIn, int oneInRich) {
        this.configName = configName;
        this.oneIn = oneIn;
        this.oneInRich = oneInRich;
    }

    /** What a server config calls this source in its list. */
    public String configName() {
        return configName;
    }

    /** How rare a pack is here, out of an ordinary find: one in this many rolls one. */
    public int oneIn() {
        return oneIn;
    }

    /**
     * The same, out of a chest this good.
     * <p>Only the chests tell the two apart. A treasure catch and a brushed block are what they
     * are wherever they happened, bar the one archaeology site that is a find in its own right.
     */
    public int oneIn(LootRichness richness) {
        return richness != null && richness.isRich() ? oneInRich : oneIn;
    }

    /** Whether this source only pays out when a player did the killing. */
    public boolean needsAPlayer() {
        return this == MOBS;
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
        if (MOBS_WORTH_FIGHTING.contains(path)) {
            return Optional.of(MOBS);
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
