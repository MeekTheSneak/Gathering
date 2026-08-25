package dev.gathering.core.ui;

import java.util.Locale;

/**
 * What colour a pack's set symbol is printed in.
 *
 * <p>Which product a pack is has to be readable at a glance in an inventory full of them, and
 * paper solves this the same way: the wrapper of a collector booster does not look like the
 * wrapper of a draft booster. The mod ships no wrapper photographs and may not take one from
 * anywhere, so the symbol carries it - one shape, printed in the colour of the product.
 *
 * <p>Black for a draft booster, gold for a set or play booster, and the mythic's own orange
 * for a collector booster. Anything else is black, which is the plainest thing a pack can be
 * and never claims to be a product it is not.
 *
 * <p>Pure.
 */
public final class PackWrapper {

    /** The ordinary pack. */
    public static final int PLAIN = 0xFF111111;

    /** A set or play booster: the pack you buy to open rather than to draft. */
    public static final int GOLD = 0xFFC8A22C;

    /** A collector booster, in the same orange a mythic glows. */
    public static final int COLLECTOR = PackGlow.MYTHIC_LIGHT;

    private PackWrapper() {
    }

    /**
     * The colour for a kind of pack, as the published data names it.
     *
     * <p>Play boosters take the set booster's gold: they replaced both it and the draft
     * booster in 2024, no set publishes both, and gold is the one that means "opened rather
     * than drafted".
     *
     * @param kind a booster kind - "draft", "set", "play", "collector"
     */
    public static int symbolColour(String kind) {
        String named = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return switch (named) {
            case "set", "play", "play-arena" -> GOLD;
            case "collector", "collector-sample" -> COLLECTOR;
            default -> PLAIN;
        };
    }
}
