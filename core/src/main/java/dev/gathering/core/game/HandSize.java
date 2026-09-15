package dev.gathering.core.game;

/**
 * Rule 402.2: a player's maximum hand size is seven, and in the cleanup step (514.1) the active
 * player discards down to it.
 * <p>Said when somebody hands the turn on holding more, and never done for them: a Reliquary
 * Tower on the table, or a card the table has never heard of, changes the number, and only the
 * player knows which.
 */
public final class HandSize {

    /** The maximum hand size with nothing changing it. */
    public static final int MAXIMUM = 7;

    private HandSize() {
    }

    /** How many cards over the maximum a hand of this size is; zero at or under it. */
    public static int overBy(int cardsInHand) {
        return Math.max(0, cardsInHand - MAXIMUM);
    }
}
