package dev.gathering.core.draft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a pod into what one drafter is allowed to know about it.
 * <p>The single place a pod becomes something that goes to a client, so there is one
 * function to read when the question is "could a drafter learn what their neighbor took".
 * It is deliberately the only way to build a {@link DraftView}: a screen that reached into
 * the state instead would be a screen holding every pack in the pod.
 */
public final class DraftVisibility {

    private DraftVisibility() {
    }

    public static DraftView viewFor(DraftState pod, DrafterId me) {
        Map<DrafterId, Integer> picked = new LinkedHashMap<>();
        Map<DrafterId, Integer> holding = new LinkedHashMap<>();
        for (int index = 0; index < pod.drafters(); index++) {
            DrafterId drafter = DrafterId.of(index);
            // Counts, never contents. How many cards somebody is holding is as visible as
            // the thickness of a pack across a table; which cards they are is not.
            picked.put(drafter, pod.poolOf(drafter).size());
            holding.put(drafter, pod.packHeldBy(drafter).size());
        }
        return new DraftView(
                me,
                pod.drafters(),
                pod.round(),
                pod.opening().size(),
                pod.isFinished(),
                pod.packHeldBy(me),
                pod.poolOf(me),
                pod.hasDeclared(me),
                pod.picksDueFrom(me),
                pod.stillToPick(),
                picked,
                holding);
    }
}
