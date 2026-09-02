package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A pod with people in it: who is drafting, where they sit in the ring, and what happens to
 * the pools when it ends.
 *
 * <p>{@link DraftState} is the cards and the rules; this is the part that knows a pick came
 * from somebody, and it holds the one thing the pod says no to. As at the table, the mod
 * enforces nothing about play - but a client may only ever act as itself, because a client
 * that could declare somebody else's pick could empty a pack it was never allowed to read
 * and learn what was in it from what came back.
 *
 * <p>Pure, so the whole of that argument is checkable in milliseconds and none of it depends
 * on a world, a block or a connection.
 *
 * @param poolsAreKept whether drafters keep what they drafted. False only for a sponsored
 *                     pod whose sponsor said at the start that the cards come back to them,
 *                     which is a decision made once, before anybody has picked, and recorded
 *                     here so nobody can change it after seeing what was opened.
 */
public record DraftPod(List<PlayerRef> drafters, DraftState state, boolean poolsAreKept) {

    public DraftPod {
        drafters = drafters == null ? List.of() : List.copyOf(drafters);
        if (state == null) {
            throw new IllegalArgumentException("A pod needs its packs");
        }
        if (drafters.size() != state.drafters()) {
            throw new IllegalArgumentException(
                    "A pod of " + state.drafters() + " packs needs " + state.drafters()
                            + " drafters, not " + drafters.size());
        }
        for (int index = 0; index < drafters.size(); index++) {
            if (drafters.get(index) == null) {
                throw new IllegalArgumentException("Nobody is drafting in place " + index);
            }
            for (int other = 0; other < index; other++) {
                if (drafters.get(other).id().equals(drafters.get(index).id())) {
                    throw new IllegalArgumentException(
                            "One player cannot hold two places in a pod: " + drafters.get(index));
                }
            }
        }
    }

    /**
     * Opens a pod with these players in this order round the ring.
     *
     * @param opening per round, per place, the pack opened there
     */
    public static DraftPod opening(
            List<PlayerRef> drafters, List<List<DraftPack>> opening, boolean poolsAreKept) {
        int size = drafters == null ? 0 : drafters.size();
        return new DraftPod(drafters, DraftState.opening(size, opening), poolsAreKept);
    }

    /** Where this player is sitting in the ring, if they are in this pod at all. */
    public Optional<DrafterId> placeOf(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        for (int index = 0; index < drafters.size(); index++) {
            if (drafters.get(index).id().equals(player)) {
                return Optional.of(DrafterId.of(index));
            }
        }
        return Optional.empty();
    }

    public Optional<PlayerRef> drafterAt(DrafterId place) {
        return place == null || place.index() >= drafters.size()
                ? Optional.empty()
                : Optional.of(drafters.get(place.index()));
    }

    public boolean isFinished() {
        return state.isFinished();
    }

    /**
     * The one thing a pod refuses.
     *
     * @return empty when the pick may proceed, or the reason it may not
     */
    public Optional<String> denialFor(UUID player, DrafterId as, List<Integer> positions) {
        DrafterId mine = placeOf(player).orElse(null);
        if (mine == null) {
            // Nobody watching a pod submits anything, and there is no place for them to
            // submit it as. A client that is not in this pod is incapable of picking.
            return Optional.of("Only drafters in this pod can pick.");
        }
        if (as != null && !as.equals(mine)) {
            // The whole of the security rule. Declaring somebody else's pick would empty a
            // pack this client may not read, and what came back would say what had been in
            // it - so a pick is always signed with the picker's own place.
            return Optional.of("A drafter can only pick for themselves.");
        }
        if (state.isFinished()) {
            return Optional.of("This pod has finished drafting.");
        }
        if (state.hasDeclared(mine)) {
            return Optional.of("You have already picked from this pack.");
        }
        int due = state.picksDueFrom(mine);
        if (due == 0) {
            return Optional.of("There is no pack in front of you.");
        }
        int asked = positions == null ? 0 : positions.size();
        if (asked != due) {
            return Optional.of("Pick " + due + " from this pack.");
        }
        // And whatever the state itself refuses - the positions being real cards in this
        // pack, and each of them named once. Asked rather than repeated, because declare
        // throws on exactly these and a rule written out twice is a rule that drifts.
        return state.denialFor(mine, positions);
    }

    /**
     * Records this player's pick, as themselves.
     *
     * @throws IllegalArgumentException with the refusal, when {@link #denialFor} refuses
     */
    public DraftPod declare(UUID player, DrafterId as, List<Integer> positions) {
        String denial = denialFor(player, as, positions).orElse(null);
        if (denial != null) {
            throw new IllegalArgumentException(denial);
        }
        DrafterId mine = placeOf(player).orElseThrow();
        return new DraftPod(drafters, state.declare(mine, positions), poolsAreKept);
    }

    /**
     * What each drafter walks away with, by place in the ring.
     *
     * <p>Empty for every place in a sponsored pod whose cards go back: the pools still exist
     * in the state, because the draft happened and its record is the record, but nobody takes
     * anything home and the screen that hands cards out must not read the state directly.
     */
    public List<List<CardIdentity>> pooledAway() {
        List<List<CardIdentity>> taken = new ArrayList<>(drafters.size());
        for (int index = 0; index < drafters.size(); index++) {
            taken.add(poolsAreKept ? state.poolOf(DrafterId.of(index)) : List.of());
        }
        return List.copyOf(taken);
    }
}
