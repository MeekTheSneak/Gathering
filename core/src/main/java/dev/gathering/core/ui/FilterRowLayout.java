package dev.gathering.core.ui;

/**
 * The row of filters across the top of the collection: a pip per color, the rarity cycle, and
 * the way through to the deck builder.
 * <p>Laid out left to right from a fixed rarity width, the last button ran off the right of a
 * 320-unit window the moment a sixth pip joined the row - which is what adding colorless did.
 * So the builder is anchored to the right edge and the rarity button takes what is left,
 * shrinking to a floor rather than pushing anything off the screen. Its label is fitted to its
 * button and the builder's would have to leave, so rarity is the right one to squeeze.
 * <p>Coordinates are GUI-scaled screen units, origin top left.
 */
public record FilterRowLayout(Rect rarity, Rect build, int pipWidth, int pipStep) {

    private static final int MARGIN = 16;
    private static final int PIP_WIDTH = 18;
    private static final int PIP_STEP = 20;
    private static final int HEIGHT = 16;
    private static final int GAP = 4;
    private static final int BEFORE_RARITY = 6;

    private static final int BUILD_WIDTH = 86;

    /** How wide the rarity button would like to be, and the least it will take. */
    private static final int RARITY_WIDTH = 92;
    private static final int RARITY_MIN = 62;

    public static int height() {
        return HEIGHT;
    }

    public static int buildWidth() {
        return BUILD_WIDTH;
    }

    /** @param pips how many color buttons the row carries - five colors and colorless */
    public static FilterRowLayout of(int screenWidth, int top, int pips) {
        int width = Math.max(1, screenWidth);
        int afterPips = MARGIN + Math.max(0, pips) * PIP_STEP;

        int buildLeft = width - MARGIN - BUILD_WIDTH;
        int rarityLeft = afterPips + BEFORE_RARITY;
        int rarityWidth =
                Math.clamp(buildLeft - GAP - rarityLeft, RARITY_MIN, RARITY_WIDTH);
        Rect rarity = new Rect(rarityLeft, top, rarityWidth, HEIGHT);
        // Never overlapping, even on a window too narrow for both at their floor: the builder
        // gives up its anchor before it gives up being clickable.
        Rect build = new Rect(Math.max(rarity.right() + GAP, buildLeft), top, BUILD_WIDTH, HEIGHT);
        return new FilterRowLayout(rarity, build, PIP_WIDTH, PIP_STEP);
    }

    /** Where the nth color pip sits. */
    public Rect pip(int index) {
        return index < 0
                ? Rect.NONE
                : new Rect(MARGIN + index * PIP_STEP, rarity.y(), PIP_WIDTH, HEIGHT);
    }
}
