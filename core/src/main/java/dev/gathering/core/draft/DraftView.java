package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import java.util.List;
import java.util.Map;

/**
 * A pod as one drafter sees it.
 *
 * <p>The same discipline the table uses for hands, for the same reason: a pick is hidden
 * information, and hidden information a client was never sent is hidden information a
 * modified client cannot read. So this carries the pack in front of this drafter and the
 * pool they have built, and about everybody else it carries numbers - how many cards they
 * are holding, how many they have taken, whether the pod is waiting on them. There is no
 * field here that could name somebody else's card, which is what makes the property easy to
 * state and easy to check.
 *
 * @param others how many cards each other drafter has picked, by place in the ring
 * @param holdingSizes how many cards each drafter is holding, which is public the way the
 *                     thickness of a pack in somebody's hand is public
 */
public record DraftView(
        DrafterId me,
        int drafters,
        int round,
        int rounds,
        boolean finished,
        DraftPack myPack,
        List<CardIdentity> myPool,
        boolean iHaveDeclared,
        int picksDueFromMe,
        List<DrafterId> waitingOn,
        Map<DrafterId, Integer> others,
        Map<DrafterId, Integer> holdingSizes) {

    public DraftView {
        myPool = myPool == null ? List.of() : List.copyOf(myPool);
        waitingOn = waitingOn == null ? List.of() : List.copyOf(waitingOn);
        others = others == null ? Map.of() : Map.copyOf(others);
        holdingSizes = holdingSizes == null ? Map.of() : Map.copyOf(holdingSizes);
    }

    /** Which way the packs are going this round, for the arrow a screen draws. */
    public int passing() {
        return finished ? 0 : DraftRules.passing(round);
    }
}
