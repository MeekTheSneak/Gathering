package dev.gathering.core.card;

/**
 * What a deck's cards look like from behind.
 *
 * <p>A real player sleeves their deck, and the sleeves are how everybody at the table tells
 * one player's cards from another's at a glance - across the table, upside down, in a stack.
 * That is the whole job here: a face-down card gives away nothing about what it is, and the
 * sleeve is the one thing about it anybody is allowed to read.
 *
 * <p>Public information, always. Sleeves are chosen before a game and seen by the whole table,
 * so this travels in the seat's view beside its life total rather than anywhere near the
 * hidden half of a board.
 *
 * <p>Two kinds. The plain ones are the sixteen dyes, tinted onto one texture - which is how
 * everything else in Minecraft comes in sixteen colors, and it means one file rather than
 * sixteen. The rest carry something printed on them, drawn from the game's own item and block
 * art rather than from anything shipped here.
 *
 * <p>The emblem's texture path is a Minecraft resource, which is an odd thing to find in the
 * pure layer. It is here because the alternative is two lists - the sleeves, and a mapping
 * from sleeve to picture - and the second one drifts from the first the first time somebody
 * adds a sleeve. Nothing in this package parses it; the client turns it into a resource.
 */
public enum Sleeve {

    /** The printed back the mod draws when nobody has chosen anything. Never tinted. */
    CLASSIC(0xFFFFFF, ""),

    // The sixteen, in the order Minecraft lists its dyes, with Minecraft's own map colors so
    // a red sleeve is the red a player already knows.
    WHITE(0xE9ECEC, ""),
    LIGHT_GRAY(0x8E8E86, ""),
    GRAY(0x3E4447, ""),
    BLACK(0x1D1D21, ""),
    BROWN(0x835432, ""),
    RED(0xB02E26, ""),
    ORANGE(0xF9801D, ""),
    YELLOW(0xFED83D, ""),
    LIME(0x80C71F, ""),
    GREEN(0x5E7C16, ""),
    CYAN(0x169C9C, ""),
    LIGHT_BLUE(0x3AB3DA, ""),
    BLUE(0x3C44AA, ""),
    PURPLE(0x8932B8, ""),
    MAGENTA(0xC74EBD, ""),
    PINK(0xF38BAA, ""),

    // And the ones with something on them. The tint is the sleeve behind the picture, picked
    // to sit under it rather than to fight it.
    GRASS(0x7A9E4B, "minecraft:textures/block/grass_block_side.png"),
    SWORD(0x6E7A86, "minecraft:textures/item/diamond_sword.png"),
    PICKAXE(0x8A8A8A, "minecraft:textures/item/iron_pickaxe.png"),
    ENDER_PEARL(0x2E5C55, "minecraft:textures/item/ender_pearl.png"),
    NETHER_STAR(0x2A2438, "minecraft:textures/item/nether_star.png"),
    REDSTONE(0x7A2622, "minecraft:textures/item/redstone.png"),
    GOLD(0x9E7A2A, "minecraft:textures/item/gold_ingot.png"),
    BOOK(0x7A5A3A, "minecraft:textures/item/book.png");

    /** What every deck sleeves up in until somebody says otherwise. */
    public static final Sleeve DEFAULT = CLASSIC;

    private final int tint;
    private final String emblem;

    Sleeve(int tint, String emblem) {
        this.tint = tint;
        this.emblem = emblem;
    }

    /**
     * The color the sleeve's own texture is multiplied by, as 0xRRGGBB.
     *
     * <p>{@link #CLASSIC} answers white, which is the same as not tinting it - so one drawing
     * path covers every sleeve rather than one path plus a special case.
     */
    public int tint() {
        return tint;
    }

    /** The picture printed on it, or empty for a plain sleeve. */
    public String emblem() {
        return emblem;
    }

    public boolean hasEmblem() {
        return !emblem.isEmpty();
    }

    /**
     * Whether this one is drawn on the printed back rather than on the plain sleeve.
     *
     * <p>Only {@link #CLASSIC} is, and it is asked as a question about the sleeve rather than
     * answered by comparing against CLASSIC at each of the places that draw one.
     */
    public boolean isPrinted() {
        return this == CLASSIC;
    }

    /**
     * The sleeve with this name, or the default.
     *
     * <p>Never throws: this reads names off a socket and out of saved decks, and a sleeve that
     * did not survive the trip is a card drawn in the ordinary back, not a disconnect.
     */
    public static Sleeve named(String name) {
        if (name == null) {
            return DEFAULT;
        }
        for (Sleeve sleeve : values()) {
            if (sleeve.name().equalsIgnoreCase(name)) {
                return sleeve;
            }
        }
        return DEFAULT;
    }

    /** The sleeve at this position in the list, or the default. Bounded, for the same reason. */
    public static Sleeve byOrdinal(int index) {
        return index >= 0 && index < values().length ? values()[index] : DEFAULT;
    }
}
