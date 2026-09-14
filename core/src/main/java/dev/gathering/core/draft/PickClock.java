package dev.gathering.core.draft;

import dev.gathering.core.game.PlayerRef;
import java.util.ArrayList;
import java.util.List;

/**
 * What a pick clock does when it runs out: takes the first cards in the pack for whoever has
 * not picked, so one drafter who walked away cannot hold eight people at the same pack.
 * <p>The first cards rather than the best or a random few. Choosing well would be the mod
 * playing the game, which it never does; choosing at random would spend server randomness on
 * something a drafter can predict anyway. The first ones are what a person reaching into a
 * pack without looking would take.
 * <p>Pure, so the whole of the rule is tested without a world or a clock.
 */
public final class PickClock {

    private PickClock() {
    }

    /**
     * Which turn the pod is on, as a number that changes every time the packs move.
     * <p>A turn is a round and how many cards are still in front of everybody: every resolved
     * turn takes at least one card out of some pack, and a new round deals full ones - so the
     * pair never repeats within a pod. Declaring does not change it, which is the point: the
     * clock runs from when the packs arrived, not from the last neighbor's pick.
     */
    public static long turnOf(DraftState state) {
        long held = 0;
        if (!state.isFinished()) {
            for (DraftPack pack : state.holding()) {
                held += pack.size();
            }
        }
        return ((long) state.round() << 32) | held;
    }

    /** Everybody the pod is still waiting on, as players. */
    public static List<PlayerRef> late(DraftPod pod) {
        List<PlayerRef> late = new ArrayList<>();
        for (DrafterId place : pod.state().stillToPick()) {
            pod.drafterAt(place).ifPresent(late::add);
        }
        return List.copyOf(late);
    }

    /**
     * The pod after everybody still to pick has taken the first cards in their pack.
     * <p>The same pod when nobody was late. Resolves the turn, because the last of the late
     * drafters declaring is what moves the packs on.
     */
    public static DraftPod pickForTheLate(DraftPod pod) {
        DraftPod now = pod;
        for (PlayerRef drafter : late(pod)) {
            DrafterId place = now.placeOf(drafter.id()).orElseThrow();
            int due = now.state().picksDueFrom(place);
            List<Integer> first = new ArrayList<>(due);
            for (int position = 0; position < due; position++) {
                first.add(position);
            }
            now = now.declare(drafter.id(), place, first);
        }
        return now;
    }

    /**
     * How many whole seconds are left, never below zero.
     *
     * @param startedAt the game tick the turn began on
     * @param now       the game tick it is
     */
    public static int secondsLeft(int pickSeconds, long startedAt, long now) {
        long left = pickSeconds * 20L - Math.max(0, now - startedAt);
        return (int) Math.max(0, (left + 19) / 20);
    }

    /** Whether the clock has run out. */
    public static boolean isUp(int pickSeconds, long startedAt, long now) {
        return pickSeconds > 0 && now - startedAt >= pickSeconds * 20L;
    }
}
