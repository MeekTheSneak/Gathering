package dev.gathering.core.draft;

/**
 * The two numbers a pod's size decides.
 *
 * <p>Kept apart from the state so the question "how many does this pod pick at a time" has
 * one answer that can be asked before a pod exists - by the screen that offers to start one,
 * and by the engine that runs it. Two places working it out separately is how a pod comes to
 * be advertised as one thing and played as another.
 */
public final class DraftRules {

    /** Below this a pod is two people passing packs back and forth, which is its own format. */
    public static final int SMALLEST_POD = 4;

    /** Above this the wheel is long enough that a pack is stale before it comes back. */
    public static final int LARGEST_POD = 8;

    /** Three packs each, which is what a draft is. */
    public static final int ROUNDS = 3;

    /**
     * The largest pod that picks two at a time.
     *
     * <p>Under six players a normal draft hands everybody far too many cards: eight packs
     * of fifteen between four is nearly a hundred picks each, and the back half of every
     * pack is chaff nobody wants. Picking two halves the passes and keeps the pod at roughly
     * the pool size a full table draft produces.
     */
    public static final int LARGEST_PICK_TWO_POD = 5;

    private DraftRules() {
    }

    public static boolean isAPodSize(int drafters) {
        return drafters >= SMALLEST_POD && drafters <= LARGEST_POD;
    }

    /** How many cards each drafter takes before the packs move on. */
    public static int picksPerTurn(int drafters) {
        require(drafters);
        return drafters <= LARGEST_PICK_TWO_POD ? 2 : 1;
    }

    /**
     * Which way the packs go in this round: {@code +1} to the drafter on the left.
     *
     * <p>Alternating, as paper does, so a pod does not spend the whole draft reading the
     * same neighbour. Rounds count from zero.
     */
    public static int passing(int round) {
        if (round < 0) {
            throw new IllegalArgumentException("A round must not be negative: " + round);
        }
        return round % 2 == 0 ? 1 : -1;
    }

    private static void require(int drafters) {
        if (!isAPodSize(drafters)) {
            throw new IllegalArgumentException(
                    "A pod is " + SMALLEST_POD + " to " + LARGEST_POD + " drafters, not " + drafters);
        }
    }
}
