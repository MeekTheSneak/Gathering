package dev.gathering.core.collection;

/**
 * Which gesture on the collection block is worth telling somebody about right now.
 *
 * <p>Two of them, and which one is offered depends on what the player is holding rather than
 * on what they can do in principle. One line at a time: two drawn at the same height would be
 * one line on top of another.
 *
 * <p>Here rather than in the screen because the rule is about Minecraft's own interaction
 * order and is easy to get subtly wrong. Crouching with anything in either hand skips block
 * interaction entirely - {@code ServerPlayerGameMode#useItemOn} decides that before the block
 * is ever asked - so the sweep is an empty-handed gesture whether or not anybody calls it one.
 * The screen offered it to anybody carrying loose cards, which is exactly the player most
 * likely to be holding one: they crouched, nothing happened, and the screen had said it would.
 *
 * <p>Dissolving is the other way round and works while crouching, because it is on the deck
 * item rather than on the block.
 */
public enum CollectionGesture {

    /** Crouch with a deck in hand to pour it back in. */
    DISSOLVE,

    /** Crouch with empty hands to put every loose card away. */
    SWEEP,

    /** Nothing worth saying. */
    NONE;

    /**
     * @param holdingDeck      whether the main hand holds a deck
     * @param handsAreEmpty    whether both hands are empty
     * @param mayAdd           whether this player is allowed to put cards in at all
     * @param carryingCards    whether there are loose cards in their inventory
     */
    public static CollectionGesture offered(
            boolean holdingDeck, boolean handsAreEmpty, boolean mayAdd, boolean carryingCards) {
        if (holdingDeck) {
            return DISSOLVE;
        }
        return mayAdd && handsAreEmpty && carryingCards ? SWEEP : NONE;
    }
}
