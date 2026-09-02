package dev.gathering.core.ui;

/**
 * What letting go of a card on the table means.
 * <p>One gesture per verb, which is the rule this exists to hold. The plainest gesture on a
 * table - press a card, let go without moving - used to tap it, so every mis-click turned
 * something sideways and a card could not be picked up and reconsidered. Tapping is E,
 * untapping is Q, and the card's own menu says so.
 * <p>Its own type because the rule is four booleans and a table of what they mean, and a rule
 * of that shape written inline in a mouse handler is a rule nothing can check. Every
 * combination is covered by a test, which is the only way "a click never taps" stays true.
 */
public enum DropMeaning {

    /** Picked up and put back. Nothing moved, so nothing is sent. */
    NOTHING,

    /** Put down where it was let go of, which is a move the table records. */
    PLACE;

    /**
     * @param fromHand whether the card was lifted out of the player's own hand
     * @param fromPile whether it was lifted off one of the zone piles
     * @param whole    whether the press had been held long enough to hold the whole stack
     * @param moved    whether the pointer travelled far enough to count as a drag
     */
    public static DropMeaning of(boolean fromHand, boolean fromPile, boolean whole, boolean moved) {
        if (moved) {
            return PLACE;
        }
        // A stack lifted off the felt and put straight back down. Emphatically not a click on
        // the card underneath, which would tap the top of a stack somebody had just decided
        // not to move.
        if (whole && !fromPile) {
            return NOTHING;
        }
        // And a card picked up off the felt and put back is a card picked up and put back.
        // A card played out of the hand still lands, because letting go of it over the table
        // is how it gets there; a press on a pile is the pile's own click and is handled
        // where the piles are.
        return !fromHand && !fromPile ? NOTHING : PLACE;
    }
}
