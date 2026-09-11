package dev.gathering.core.ui;

import java.util.List;

/**
 * How many things one gesture is allowed to act on.
 * <p>A selection is made by dragging a box, and a box can be dragged around the whole table.
 * Every verb that works on a selection then sends one move per card, so the size of a
 * selection is the size of a burst of packets - and nothing anywhere decided how big that
 * was allowed to get. One gesture could make unbounded work.
 * <p>The bound is deliberately set above any real board rather than at a comfortable-looking
 * number. Wiping a crowded Commander table is a legitimate thing to do with one gesture, and
 * silently doing sixty-four of ninety-one cards would be a worse bug than the one this
 * prevents: the player would look at the felt, see cards still on it, and have no idea why.
 * So this is a ceiling on the pathological case, not a budget for the ordinary one - and when
 * it does bite, the caller is told exactly how much it took, so it can say so.
 * <p>Pure. It knows nothing about cards; it counts.
 */
public final class BulkLimit {

    /**
     * The most things one gesture may act on.
     * <p>Above any board that has ever been photographed by the scripted run, and far below
     * the number at which a burst of moves is a problem for anybody. See the note above about
     * why it is not tighter.
     */
    public static final int MOST_AT_ONCE = 128;

    private BulkLimit() {
    }

    /**
     * What a gesture will actually act on, and what it was asked to act on.
     *
     * @param doing    the things that will be acted on, in the order they were given
     * @param askedFor how many there were before the bound was applied
     */
    public record Batch<T>(List<T> doing, int askedFor) {

        public Batch {
            doing = doing == null ? List.of() : List.copyOf(doing);
        }

        /** How many were left out, which is zero unless the bound bit. */
        public int leftOut() {
            return Math.max(0, askedFor - doing.size());
        }

        /** Whether anything was left out, and the player therefore has to be told. */
        public boolean wasClipped() {
            return leftOut() > 0;
        }

        /** How many will be acted on. */
        public int size() {
            return doing.size();
        }

        public boolean isEmpty() {
            return doing.isEmpty();
        }
    }

    /**
     * Takes as many as one gesture is allowed, keeping the order they came in.
     * <p>The first of them rather than a sample: the order a selection is in is the order the
     * cards were picked up in, and taking the front of it is the only choice that is the same
     * twice for the same gesture.
     */
    public static <T> Batch<T> take(List<T> targets) {
        if (targets == null || targets.isEmpty()) {
            return new Batch<>(List.of(), 0);
        }
        int asked = targets.size();
        return asked <= MOST_AT_ONCE
                ? new Batch<>(targets, asked)
                : new Batch<>(targets.subList(0, MOST_AT_ONCE), asked);
    }
}
