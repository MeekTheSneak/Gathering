package dev.gathering.core.tutorial;

import java.util.Map;

/**
 * Whether a lesson step has actually been done, read off two boards.
 * <p>The lesson used to compare totals: tap something and the number of tapped cards goes up, so the
 * step is done. That is true right up until the learner does two things, which in a tutorial is what
 * learners do. Tap one card and untap another and the total is where it started, so the step sits there
 * having watched them do the thing it asked for. Put a counter on one card and take one off another and
 * the same.
 * <p>So the question asked here is not "is there more of it" but "did any one of them gain": a card that
 * is tapped now and was not before, whatever else happened elsewhere on the board. Nothing anybody does
 * to a second card can mask what they did to the first.
 * <p>Pure, and kept out of the client on purpose - the client owns reading a board, and this owns the one
 * rule that decides whether somebody has learned something.
 */
public final class TutorialEvidence {

    private TutorialEvidence() {
    }

    /**
     * Whether anything in the second reading is higher than it was in the first.
     * <p>A key missing from either side counts as nought, so a card that has appeared since the step
     * began - played, or a token made - can satisfy it as readily as one that was already there.
     *
     * @param before what each thing was worth when the step went up
     * @param now    what each is worth on the board that has just arrived
     */
    public static <K> boolean anyRose(Map<K, Integer> before, Map<K, Integer> now) {
        if (now == null || now.isEmpty()) {
            return false;
        }
        for (Map.Entry<K, Integer> entry : now.entrySet()) {
            int was = before == null ? 0 : before.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() != null && entry.getValue() > was) {
                return true;
            }
        }
        return false;
    }
}
