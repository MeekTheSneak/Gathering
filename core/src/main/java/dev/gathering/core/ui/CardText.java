package dev.gathering.core.ui;

/**
 * How big the writing on a card is drawn, against the card rather than against the screen.
 * <p>Reported from a real session: "text scaling with zoom in and out inadvertently causes a
 * lot of visual issues. text should probably just render at a set appropriate size on
 * everything, and when you zoom in it gets bigger, zoom out it gets smaller."
 * <p>That reverses an earlier call. A four-player session had asked for the opposite -
 * "writing on cards should not scale with scrolling out - stay one size" - and one size is
 * what it got: a note and a counter were drawn at the font's own size whatever size the card
 * under them was. It reads well at one zoom and badly at every other, because writing pinned
 * to the screen grows against the card as the board shrinks until it is a band of letters
 * with a picture behind it.
 * <p>So the writing is a fraction of the card, which is what it is on a real card, and it
 * comes to the same thing at the size the old number was chosen at. The floor is the honest
 * part of the old request: below it a note is a smudge and is not drawn at all rather than
 * drawn illegibly small.
 * <p>These are the same fractions the board on the block uses, so a card carries the same
 * writing at the same size in both views.
 * <p>Pure. Font metrics are measured by the caller and handed in.
 */
public final class CardText {

    /** How tall a note across the top of a card is, against the card's own height. */
    public static final float NOTE = 0.14f;

    /** And a counter along its bottom, which is a shorter line and there may be several. */
    public static final float COUNTER = 0.12f;

    /**
     * The smallest writing worth drawing, as a multiple of the font's own size.
     * <p>Under this a word is a smudge, so whatever would be written this small is not
     * written. The mod's one answer to "as small as text goes", rather than a second number
     * beside it that could drift away.
     */
    public static final float SMALLEST = TextScale.SMALLEST;

    /**
     * The largest, so a card filling the window does not get writing across it like a poster.
     * <p>A note is an annotation on a picture. Past about half again the font's size it stops
     * reading as one and starts reading as the card's own text.
     */
    public static final float LARGEST = 1.5f;

    static {
        if (LARGEST > TextScale.LARGEST) {
            throw new IllegalStateException(
                    "writing on a card may not be drawn larger than anything else is");
        }
    }

    private CardText() {
    }

    /**
     * What to draw a line of writing at on a card this tall.
     * <p>Never smaller than the floor: what is drawn is always drawn at a size somebody can
     * read. Whether it should be drawn at all when the card has got that small is
     * {@link #worthDrawing}'s, and the two answers differ by what the writing is.
     *
     * @param cardHeight how tall the card is being drawn
     * @param fraction   how much of it one line should take - {@link #NOTE} or {@link #COUNTER}
     * @param lineHeight the font's own line height
     */
    public static float scaleFor(int cardHeight, float fraction, int lineHeight) {
        if (cardHeight <= 0 || lineHeight <= 0 || fraction <= 0f) {
            return SMALLEST;
        }
        return Math.clamp(cardHeight * fraction / lineHeight, SMALLEST, LARGEST);
    }

    /**
     * Whether a card this small is worth writing a whole sentence on.
     * <p>For a note, which is words: below the floor it would be trimmed to a letter and an
     * ellipsis, and that is a smudge rather than information, so nothing is written.
     * <p>Not for a counter. A counter is two or three characters and it is the difference
     * between a 2/2 and a 4/4 - a board zoomed out that stopped saying which creatures have
     * counters on them would be hiding the thing the player zoomed out to see. Those are
     * drawn at the floor and stay there.
     */
    public static boolean worthDrawing(int cardHeight, float fraction, int lineHeight) {
        return cardHeight > 0 && lineHeight > 0 && fraction > 0f
                && cardHeight * fraction / lineHeight >= SMALLEST;
    }

    /** How tall a line drawn at that scale comes out, for laying out what sits around it. */
    public static int lineAt(float scale, int lineHeight) {
        return Math.max(1, Math.round(lineHeight * scale));
    }
}
