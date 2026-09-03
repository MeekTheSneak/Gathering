package dev.gathering.core.ui;

/**
 * How big the badge behind a card's numbers is, and how big the numbers in it are.
 * <p>Reported from a real session: "the box containing loyalty counters and power toughness
 * does not render large enough to contain the numbers". It was sized by clamping the text's
 * own width to the card - so a three-digit loyalty, or a 12/12 on a card at the far end of a
 * crowded table, gave a badge that stopped at the card's edge while the numbers carried on
 * past both sides of it.
 * <p>So the text is fitted first and the badge is measured from the fitted text. The one thing
 * this exists to guarantee is that {@link #holdsItsNumbers} is true for every card size and
 * every number anybody can write, which is a fact about arithmetic and is checked as one.
 * <p>Pure. Font widths are measured by the caller and handed in; nothing here draws.
 */
public final class StrengthBadge {

    /** How much badge there is around its numbers, on every side. */
    public static final int PADDING = 2;

    /**
     * How small the numbers may be squeezed before they stop being readable.
     * <p>Below this the badge is allowed to be wider than the card rather than the numbers
     * being made smaller still: numbers nobody can read are worse than a badge that sticks
     * out, and both are better than numbers drawn outside the box that is meant to hold them.
     */
    public static final float SMALLEST = 0.5f;

    /**
     * The narrowest card worth putting a badge on.
     * <p>Below this there is no room for the padding, let alone a digit, and a badge would be
     * wider than the card it is meant to be a corner of. Callers ask {@link #fitsOn} and draw
     * nothing rather than drawing something that hangs off both sides.
     */
    public static final int MINIMUM_ROOM = PADDING * 2 + 1;

    /**
     * A badge, in pixels, placed relative to the bottom right of the card it is on.
     *
     * @param width  how wide the badge is
     * @param height how tall it is
     * @param scale  what to draw the numbers at
     * @param textX  where the middle of the numbers goes, from the badge's left edge
     * @param textY  where the top of the numbers goes, from the badge's top edge
     */
    public record Fit(int width, int height, float scale, int textX, int textY) {
    }

    private StrengthBadge() {
    }

    /** Whether a card this wide has room for a badge at all. */
    public static boolean fitsOn(int room) {
        return room >= MINIMUM_ROOM;
    }

    /**
     * Works the badge out for numbers this wide in a card this wide.
     *
     * @param textWidth  how wide the numbers are at full size
     * @param lineHeight how tall a line of them is at full size
     * @param room       how much of the card the badge may take up
     */
    public static Fit of(int textWidth, int lineHeight, int room) {
        int wide = Math.max(1, textWidth);
        int space = Math.max(1, room - PADDING * 2);
        float scale = wide <= space ? 1f : Math.max(SMALLEST, (float) space / wide);

        int drawn = Math.round(wide * scale);
        int width = drawn + PADDING * 2;
        int height = Math.round(lineHeight * scale) + PADDING;
        return new Fit(width, height, scale, width / 2,
                Math.round((height - lineHeight * scale) / 2f));
    }

    /** Whether a fit really holds the numbers it was worked out for. */
    public static boolean holdsItsNumbers(Fit fit, int textWidth, int lineHeight) {
        return fit != null
                && Math.round(textWidth * fit.scale()) + PADDING * 2 <= fit.width()
                && Math.round(lineHeight * fit.scale()) <= fit.height()
                && fit.textY() >= 0
                && fit.textY() + Math.round(lineHeight * fit.scale()) <= fit.height();
    }
}
