package dev.gathering.core.ui;

/**
 * How brightly a card that somebody has pointed at is ringed, at a moment.
 * <p>"In response to that" needs a "that". Pointing at a card is the one gesture at this table
 * that changes nothing about the game and is worth having anyway - it is what a finger does
 * across a real table, and at a four-player board where three of the felt mats are somewhere
 * other than where you are looking, a card that briefly rings is the difference between a
 * sentence people can follow and one they cannot.
 * <p>It used to do neither of the things its own description promised. The event's javadoc
 * said it "highlights a public card for everyone for a few seconds"; what it actually did was
 * write a line in the log, which is the one place nobody is looking while somebody is pointing
 * at something.
 * <p>Pure, and a function of the time since it started rather than of a counter somebody has to
 * remember to advance - the same reason {@link Shaking} is. Two clients ring the same card for
 * the same length, and a dropped frame is a frame missed rather than a ring that runs long.
 */
public final class Pointing {

    /**
     * How long a card stays ringed, in milliseconds.
     * <p>Long enough to look up and find it - the point is somebody across the table saying
     * "that one" and everybody else finding it - and short enough that a board where four
     * people are talking does not stay lit.
     */
    public static final long LASTS = 2_600L;

    /** How many times it pulses while it lasts. Slow: this is a beacon, not an alarm. */
    private static final double PULSES = 3.0;

    /** The thinnest and thickest the ring gets, as a fraction of a card's short side. */
    private static final double THINNEST = 0.03;
    private static final double THICKEST = 0.075;

    private Pointing() {
    }

    /**
     * How strong the ring is: one at its brightest, nought once it is over.
     * <p>Pulsing, and fading as it goes. A ring at a steady brightness reads as a state the
     * card is in - selected, or targeted by something - rather than as somebody pointing;
     * a pulse reads as a gesture, which is what this is.
     */
    public static float strength(long since) {
        if (since < 0 || since >= LASTS) {
            return 0f;
        }
        double through = since / (double) LASTS;
        // Fading out over the whole span, so the last pulse is the faintest rather than the
        // ring simply vanishing mid-flash.
        double fading = 1.0 - through;
        // Starts bright rather than at nothing: a ring that eases in is a ring nobody catches
        // the beginning of, and the beginning is when somebody said "that one".
        double pulse = 0.55 + 0.45 * Math.cos(through * PULSES * 2 * Math.PI);
        return (float) Math.max(0, Math.min(1, fading * pulse));
    }

    /**
     * How thick to draw the ring on a card of this size, in pixels.
     * <p>Measured off the card rather than fixed, because the same board is drawn at a dozen
     * zoom levels and a two-pixel ring on a card zoomed out is a smudge on the felt.
     */
    public static int thickness(int cardShortSide, long since) {
        float strength = strength(since);
        if (strength <= 0f) {
            return 0;
        }
        double fraction = THINNEST + (THICKEST - THINNEST) * strength;
        // Two rather than one. The board is drawn at a dozen zoom levels and a card can be
        // twelve pixels across; a single-pixel ring at that size is a slightly different
        // shade of edge, which is the feature quietly not existing at exactly the zoom where
        // finding one card among forty is hardest.
        return Math.max(2, (int) Math.round(cardShortSide * fraction));
    }

    /**
     * The ring's color at a moment, alpha included.
     * <p>One hue, with only the alpha moving. A ring that changed color would be saying two
     * things - "look here" and something about what kind of card it is - and it only has one
     * thing to say.
     *
     * @param rgb the ring's color without alpha, which the theme chooses
     */
    public static int color(int rgb, long since) {
        int alpha = Math.round(255 * strength(since));
        return alpha <= 0 ? 0 : (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
