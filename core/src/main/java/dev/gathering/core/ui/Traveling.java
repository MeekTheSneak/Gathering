package dev.gathering.core.ui;

/**
 * Where something on its way from one rectangle to another is, part of the way through.
 *
 * <p>Eased rather than straight: a card that sets off at full speed and stops dead is a card
 * being teleported in instalments. It leaves gently, crosses quickly and settles - which is
 * how a hand puts a card down, and is the whole reason for drawing the journey at all.
 */
public final class Traveling {

    private Traveling() {
    }

    /**
     * The eased fraction: nought at the start, one at the end, quickest in the middle.
     *
     * <p>Smoothstep. Its slope is nought at both ends, so a card neither jumps as it leaves
     * nor stops as it lands.
     */
    public static float eased(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        return clamped * clamped * (3f - 2f * clamped);
    }

    /**
     * The rectangle a card is filling part of the way across.
     *
     * <p>The size travels too. A card leaving a hand for a pile slot is a different size at
     * each end - a mat drawn small has small slots - and a card that kept one size until it
     * arrived and then snapped to the other would be two teleports instead of one.
     */
    public static Rect between(Rect from, Rect to, float progress) {
        if (from.isEmpty()) {
            return to;
        }
        if (to.isEmpty()) {
            return from;
        }
        float eased = eased(progress);
        return new Rect(
                lerp(from.x(), to.x(), eased),
                lerp(from.y(), to.y(), eased),
                Math.max(1, lerp(from.width(), to.width(), eased)),
                Math.max(1, lerp(from.height(), to.height(), eased)));
    }

    private static int lerp(int from, int to, float at) {
        return Math.round(from + (to - from) * at);
    }
}
