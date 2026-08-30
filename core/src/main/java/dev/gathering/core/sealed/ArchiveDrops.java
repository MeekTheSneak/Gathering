package dev.gathering.core.sealed;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the Archive Pack comes from, and how rarely.
 *
 * <p>The Archive Pack is the closing move on completeness. Everything else a server can hand
 * out comes off a print sheet or a shop shelf; whatever those between them cannot reach - an
 * old promo, a buy-a-box card, the long tail of a set nobody sells any more - is exactly what
 * this pack holds, and it shrinks by computation as a server adds products. A server whose
 * faucets already cover its catalog has an empty archive and drops none of these, which is
 * the goal rather than a fault.
 *
 * <p>Because it is the only path to those cards, it is not a shop item and never will be:
 * something you can buy is not a long tail, it is a shelf. It comes out of the three places
 * worth going to - treasure at the bottom of the sea, the chests people build expeditions
 * around, and the bosses - and out of nowhere else.
 *
 * <p>Which tables those are is a rule rather than a setting, for the reason every other loot
 * rule here is: a server owner picks whether their players find cards at all, not whether an
 * ancient city is a better find than a village.
 *
 * <p>Pure.
 */
public enum ArchiveDrops {

    /** Killed something the world is built around. Rare enough that it can be generous. */
    BOSS(2),

    /** A chest at the end of something. The same list {@link LootRichness} calls rich. */
    EXPEDITION(30),

    /** Pulled out of the sea, which is the slow way in and always has been. */
    TREASURE(60);

    /**
     * The bosses, by the loot table each drops through.
     *
     * <p>Named rather than matched on a prefix: {@code entities/} is every mob in the game,
     * and an archive pack out of a zombie is the opposite of what this is for.
     */
    private static final List<String> BOSSES = List.of(
            "entities/ender_dragon",
            "entities/wither",
            "entities/warden",
            "entities/elder_guardian");

    private final int oneIn;

    ArchiveDrops(int oneIn) {
        this.oneIn = oneIn;
    }

    /** One in this many of these rolls an archive pack. */
    public int oneIn() {
        return oneIn;
    }

    /**
     * Whether an archive pack can come out of this loot table, and how often.
     *
     * <p>Minecraft's own tables only, like everything else here: a pack falling out of
     * somebody else's dungeon is a surprise nobody asked this mod for.
     *
     * @param tableId a loot table's full id, namespace and all
     */
    public static Optional<ArchiveDrops> of(String tableId) {
        String id = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
        if (!id.startsWith("minecraft:")) {
            return Optional.empty();
        }
        String path = id.substring("minecraft:".length());
        if (BOSSES.contains(path)) {
            return Optional.of(BOSS);
        }
        if (path.equals("gameplay/fishing/treasure")) {
            return Optional.of(TREASURE);
        }
        // Deliberately the same list the ordinary pack calls a good chest, rather than a
        // second one beside it. Two lists of which chests are worth something is two lists
        // that disagree the first time only one of them learns about a new structure.
        return LootRichness.of(id).isRich() ? Optional.of(EXPEDITION) : Optional.empty();
    }

    /** How many cards an archive pack holds. Small: every one of them is a card nothing else reaches. */
    public static final int CARDS = 3;
}
