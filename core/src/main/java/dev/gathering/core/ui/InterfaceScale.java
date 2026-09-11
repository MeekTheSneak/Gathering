package dev.gathering.core.ui;

/**
 * How far the interface may be scaled up or down, as a percentage.
 * <p>One pair of numbers, in core, because three separate things need them and they had begun
 * to disagree: the settings clamped to 75 and 200, the panel that lays those settings out
 * clamped to 50 and 200, and the menu sized its rows against a third pair written beside it.
 * Nothing visibly broke, because the value reaching each of them had already been clamped by
 * the first - but a row offering 50% while the floor was 75% showed one number, stored another
 * and drew at the second, and every part of it looked right on its own.
 *
 * <p>Here rather than in the client settings because core cannot see the client, and the
 * layout that needs them lives here. The settings own the <em>value</em>; this owns the
 * <em>range</em>.
 *
 * <p>Not the same thing as {@link TextScale}, which is how small a line may be squeezed to fit
 * a space it has to go in. That is a fitting decision the interface makes; this is a
 * preference the player sets. They are near neighbours and have been confused before, which is
 * why this says so.
 *
 * <p>Pure.
 */
public final class InterfaceScale {

    /**
     * The smallest the interface may be asked to be, as a percentage.
     * <p>Below this the mod's own rows stop being reliably clickable at ordinary GUI scales,
     * which is the opposite of what somebody turning it down is usually after.
     */
    public static final int SMALLEST_PERCENT = 75;

    /** The largest. Twice the shipped size, which is where a panel stops fitting a window. */
    public static final int LARGEST_PERCENT = 200;

    private InterfaceScale() {
    }

    /** This percentage, brought inside the range. */
    public static int sane(int percent) {
        return Math.clamp(percent, SMALLEST_PERCENT, LARGEST_PERCENT);
    }

    /** This percentage as a multiplier, brought inside the range first. */
    public static float asFraction(int percent) {
        return sane(percent) / 100f;
    }
}
