package dev.gathering.core.ui;

import java.util.Locale;

/**
 * What color a pack's set symbol is printed in.
 * <p>Which product a pack is has to be readable at a glance in an inventory full of them, and
 * paper solves this the same way: the wrapper of a collector booster does not look like the
 * wrapper of a draft booster. The mod ships no wrapper photographs and may not take one from
 * anywhere, so the symbol carries it - one shape, printed in the color of the product.
 * <p>Black for a draft booster, gold for a set or play booster, and the mythic's own orange
 * for a collector booster. Anything else is black, which is the plainest thing a pack can be
 * and never claims to be a product it is not.
 * <p>Pure.
 */
public final class PackWrapper {

    /** The ordinary pack. */
    public static final int PLAIN = 0xFF111111;

    /** A set or play booster: the pack you buy to open rather than to draft. */
    public static final int GOLD = 0xFFC8A22C;

    /** A collector booster, in the same orange a mythic glows. */
    public static final int COLLECTOR = PackGlow.MYTHIC_LIGHT;

    /**
     * Where the wrapper's picture is, so the screen that tears one open draws it at the shape it was drawn
     * at.
     * <p>The picture is sixteen pixels square: two blank columns either side of a twelve-pixel bag, four
     * rows of crimp at the top and twelve of body below it. The screen used to cut rows one to fourteen out
     * of it and lay them along a pack two thirds as wide as it was tall, which stretched the bag every way
     * at once - the owner saw it as soon as a pack opened (2026-09-16). The numbers live here, where
     * something without a window can check they still add up to the whole picture.
     */
    public static final int PIXELS = 16;

    /** Blank columns either side of the bag. */
    public static final int MARGIN = 2;

    /** The crimped strip at the top, which is where a pack is torn. */
    public static final int CRIMP_ROW = 0;
    public static final int CRIMP_ROWS = 4;

    /** The body below it, down to the fold at the bottom. */
    public static final int BODY_ROW = CRIMP_ROW + CRIMP_ROWS;
    public static final int BODY_ROWS = PIXELS - BODY_ROW;

    /** How wide a pack is drawn against its height: the printed bag's own shape. */
    public static double shape() {
        return (PIXELS - 2.0 * MARGIN) / PIXELS;
    }

    /** How much of a pack's height the crimp takes, which is where the tear runs. */
    public static double crimp() {
        return CRIMP_ROWS / (double) PIXELS;
    }

    private PackWrapper() {
    }

    /**
     * The color for a kind of pack, as the published data names it.
     * <p>Play boosters take the set booster's gold: they replaced both it and the draft
     * booster in 2024, no set publishes both, and gold is the one that means "opened rather
     * than drafted".
     *
     * @param kind a booster kind - "draft", "set", "play", "collector"
     */
    public static int symbolColor(String kind) {
        String named = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return switch (named) {
            case "set", "play", "play-arena" -> GOLD;
            case "collector", "collector-sample" -> COLLECTOR;
            default -> PLAIN;
        };
    }
}
