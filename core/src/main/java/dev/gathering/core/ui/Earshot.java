package dev.gathering.core.ui;

/**
 * How far a table's own noises carry, and what to do about the one that has to arrive anyway.
 * <p>Every sound a table makes is played at the table, which is right: a shuffle two rooms away is
 * somebody else's game and should sound like it. The turn coming round to you is the exception. It
 * is the one event you have to act on, it is the reason the notification exists at all, and you are
 * most likely to miss it exactly when you have walked off to a chest - which is also the moment a
 * sound at the table cannot reach you.
 * <p>So past this distance the turn is played where the player is and said on the screen in words,
 * and inside it the table speaks for itself as everything else does.
 * <p>Pure, so the number is checked rather than guessed at from a chair.
 */
public final class Earshot {

    /**
     * How far a table is heard from, in blocks.
     * <p>Minecraft carries a sound played at a block about sixteen blocks times its volume, and the
     * table's noises are played a little over half - so this is where a table stops being audible
     * rather than a number chosen for its own sake.
     */
    public static final double BLOCKS = 9.0;

    private Earshot() {
    }

    /** Whether a table this far away, squared, can still be heard. */
    public static boolean carries(double distanceSquared) {
        return distanceSquared <= BLOCKS * BLOCKS;
    }

    /**
     * Whether the turn has to be brought to the player rather than left at the table.
     * <p>The same question the other way round, named for what the caller is deciding.
     */
    public static boolean mustFollowThePlayer(double distanceSquared) {
        return !carries(distanceSquared);
    }
}
