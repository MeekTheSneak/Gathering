package dev.gathering.core.ui;

/**
 * How brightly a pile glows after cards have just arrived in it.
 * <p>A card flies from where it was to where it went, and that is enough while you are watching.
 * In a game of four you mostly are not: three boards are somewhere other than where you are
 * looking, and a card that landed in somebody's graveyard while you read your hand left no mark.
 * So the pile it landed in glows for a moment in its seat's color - the way a pile lights up when
 * you hold a card over it, but for somebody else's move, and fading on its own.
 * <p>In the seat's color rather than a color of its own, so a glow reads as "this seat's pile" at
 * a glance, and is never confused with a card somebody points at, which rings in warm gold.
 * <p>For somebody who asked for less motion it holds steady and then goes, rather than fading:
 * a fade is motion, and the mark is the information.
 * <p>Pure, and a function of the time since the cards landed, so the board on the screen and the
 * table in the world glow the same pile for the same length.
 */
public final class Arrival {

    /**
     * How long a pile glows once cards land in it, in milliseconds.
     * <p>Long enough to be caught on a glance back up from a hand, short enough that a busy turn
     * is not a table that is permanently lit.
     */
    public static final long LASTS = 1_400L;

    /** How strong a glow is held for, with reduced motion, as a fraction of the full one. */
    public static final float STEADY = 0.7f;

    private Arrival() {
    }

    /**
     * How strongly a pile glows this long after cards landed in it: one as they land, nothing
     * once it is over, and nothing before they have landed - a card still in the air has not
     * arrived.
     */
    public static float strength(long sinceLanding, boolean reducedMotion) {
        if (sinceLanding < 0 || sinceLanding >= LASTS) {
            return 0f;
        }
        if (reducedMotion) {
            return STEADY;
        }
        float left = 1f - sinceLanding / (float) LASTS;
        return left * left;
    }

    /** The glow's alpha for a strength, out of 255: never quite solid, so the pile shows through. */
    public static int alpha(float strength) {
        return Math.round(Math.max(0f, Math.min(1f, strength)) * 0xB0);
    }
}
