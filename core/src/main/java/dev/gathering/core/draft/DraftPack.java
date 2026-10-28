package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import java.util.List;

/**
 * A pack as it goes round the ring: the cards still in it, in the order they were laid out.
 * <p>Order is kept rather than treated as a set, because a pack is a physical object being
 * handed to the next player and two drafters looking at the same pack must see the same
 * thing in the same places. It is also what makes a pick reproducible from the log: a pick
 * is an index into this list, so replaying a pod's picks against its opening packs gives
 * back exactly the pools it gave the first time.
 */
public record DraftPack(List<CardIdentity> cards) {

    public DraftPack {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    public static DraftPack of(List<CardIdentity> cards) {
        return new DraftPack(cards);
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public int size() {
        return cards.size();
    }

    public CardIdentity at(int index) {
        return cards.get(index);
    }

    /**
     * The pack with these positions taken out of it.
     * <p>All of them at once rather than one at a time, because a pick-two is one decision:
     * taken one after the other, the second index would mean a different card than the one
     * the drafter pointed at, since the first removal has already shifted everything after
     * it along. That is the bug this signature exists to make impossible.
     */
    public DraftPack without(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) {
            return this;
        }
        List<CardIdentity> left = new java.util.ArrayList<>(cards.size());
        for (int index = 0; index < cards.size(); index++) {
            if (!positions.contains(index)) {
                left.add(cards.get(index));
            }
        }
        return new DraftPack(left);
    }

    /** The cards at these positions, in the order the pack holds them. */
    public List<CardIdentity> at(List<Integer> positions) {
        List<CardIdentity> taken = new java.util.ArrayList<>(positions.size());
        for (int index = 0; index < cards.size(); index++) {
            if (positions.contains(index)) {
                taken.add(cards.get(index));
            }
        }
        return List.copyOf(taken);
    }
}
