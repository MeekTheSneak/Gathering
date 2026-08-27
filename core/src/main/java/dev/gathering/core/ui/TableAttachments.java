package dev.gathering.core.ui;

/**
 * Where the cards sitting on a card are drawn.
 *
 * <p>Small, and fanned down the left of the card they are on, the way a stack of equipment
 * sits under the creature holding it on a real table. Small because the host is the thing you
 * are reading and its attachments are a list of modifiers; fanned because two auras have to be
 * visibly two.
 *
 * <p>Fanned, not spread. Each one overlaps the one above it and leaves a strip showing, which
 * is what a real stack of cards does and what lets six fit beside a card rather than four
 * running off the table. What matters is that the strip is big enough to see and to click,
 * not that the cards are clear of each other.
 *
 * <p>Deliberately not centered on the host. A card drawn on top of another one hides it, and
 * the whole reason to have this rather than letting people stack cards by hand is that a
 * stacked aura is an aura nobody can see.
 */
public final class TableAttachments {

    /** How big an attachment is drawn, against the card it is on. */
    public static final float SCALE = 0.5f;

    /** How far down each one sits from the last, as a fraction of the host's height. */
    private static final float STEP = 0.26f;

    /** The least a strip may be and still be worth calling visible. */
    private static final int MINIMUM_STRIP = 5;

    /** How far the whole fan sits to the left of the host, as a fraction of its width. */
    private static final float INSET = 0.62f;

    /** Where the fan starts, as a fraction of the host's height down from its top. */
    private static final float FIRST = 0.06f;

    private TableAttachments() {
    }

    /**
     * The nth attachment on a card drawn at {@code host}.
     *
     * <p>Runs down the left rather than up, so the first one attached stays where it was as
     * more arrive - a fan that grows upwards renumbers itself every time somebody equips
     * something, and the card you were about to click moves.
     */
    public static Rect slot(Rect host, int index) {
        int width = Math.max(1, Math.round(host.width() * SCALE));
        int height = Math.max(1, Math.round(host.height() * SCALE));
        return new Rect(
                host.x() - Math.round(host.width() * INSET),
                host.y() + Math.round(host.height() * FIRST)
                        + visibleStrip(host) * Math.max(0, index),
                width,
                height);
    }

    /**
     * The same fan, mirrored to the right.
     *
     * <p>For a host near the left edge of the table, where the fan would otherwise be drawn
     * off the surface and half of somebody's equipment would be invisible.
     */
    public static Rect slotOnTheRight(Rect host, int index) {
        Rect left = slot(host, index);
        return new Rect(
                host.right() + Math.round(host.width() * (INSET - SCALE)),
                left.y(),
                left.width(),
                left.height());
    }

    /**
     * How much of each attachment shows past the one drawn over it.
     *
     * <p>The number that decides whether a fan of six is six cards or one card, so it is
     * floored rather than left to shrink with the card size.
     */
    public static int visibleStrip(Rect host) {
        return Math.max(MINIMUM_STRIP, Math.round(host.height() * STEP));
    }

    /** Which side of the host the fan fits on, given the surface it has to stay inside. */
    public static boolean fansLeft(Rect host, Rect surface) {
        return slot(host, 0).x() >= surface.x();
    }
}
