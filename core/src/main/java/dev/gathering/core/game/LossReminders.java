package dev.gathering.core.game;

/**
 * The totals at which the rules say a player has lost - shown, never acted on.
 * <p>What a life pad does: stop somebody counting to twenty-one in their head three times a
 * turn. The mod never ends a game over any of these; a player at zero life can still be saved
 * by a card the table has never heard of, and the table would be wrong to decide otherwise.
 * <p>One place for the numbers, so every screen that colors a total agrees about when.
 */
public final class LossReminders {

    /** Rule 104.3b: a player with 0 or less life loses. */
    public static final int LIFE = 0;

    /** Rule 104.3d: a player with ten or more poison counters loses. */
    public static final int POISON = 10;

    /** Rule 903.10a: 21 combat damage from one commander loses the game. */
    public static final int COMMANDER_DAMAGE = 21;

    private LossReminders() {
    }

    public static boolean lifeIsAtALoss(int life) {
        return life <= LIFE;
    }

    public static boolean poisonIsAtALoss(int poison) {
        return poison >= POISON;
    }

    public static boolean commanderDamageIsAtALoss(int damage) {
        return damage >= COMMANDER_DAMAGE;
    }

    /** Whether this counter on a seat is one the rules count a loss by, at this amount. */
    public static boolean counterIsAtALoss(String counter, int amount) {
        return SeatState.Counters.POISON.equals(counter) && poisonIsAtALoss(amount);
    }
}
