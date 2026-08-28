package dev.gathering.core.game.event;

/**
 * What comes up on a planar die.
 *
 * <p>Six faces and three of them: one that planeswalks, one that causes chaos, and four blanks.
 * Not a d6, because the faces are symbols rather than numbers and a log line reading "rolled a
 * 3" would be true and useless - which is the same reason a coin is heads and tails here
 * rather than a two-sided die.
 *
 * <p>Planechase itself is not implemented and is not planned: a planar deck belongs to the
 * table rather than to a seat, and every zone in this game is owned by a seat. The die is here
 * because it is the part of Planechase that cannot be done by hand honestly - somebody rolling
 * their own planar die is somebody claiming a chaos symbol - and because a group can play the
 * rest of it manually, the way they would across a table.
 *
 * <p>Pure.
 */
public enum PlanarFace {

    /** Nothing. Four of the six, which is why planeswalking costs what it costs. */
    BLANK,

    /** The chaos symbol: the plane's middle ability triggers. */
    CHAOS,

    /** The planeswalk symbol: this plane goes to the bottom and the next one comes up. */
    PLANESWALK;

    /** How many faces the die has, which is what the roll is taken out of. */
    public static final int FACES = 6;

    /**
     * What face a roll of the die landed on.
     *
     * <p>One chaos, one planeswalk, four blanks, in that order - so the odds are the printed
     * ones rather than a third each. Taken as a number from outside so the randomness stays
     * where all of this mod's randomness is: on the server, in the level's own generator,
     * never anywhere near the session seed.
     */
    public static PlanarFace of(int roll) {
        int face = Math.floorMod(roll, FACES);
        if (face == 0) {
            return CHAOS;
        }
        return face == 1 ? PLANESWALK : BLANK;
    }
}
