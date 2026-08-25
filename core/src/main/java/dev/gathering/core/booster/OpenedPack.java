package dev.gathering.core.booster;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.List;

/**
 * What came out of a pack, in the order it should be shown.
 *
 * <p>Ordered on purpose. Opening a booster is a ceremony - you go through the commons, then
 * the uncommons, then the thing you were actually waiting for - and a pack handed over as an
 * unordered bag throws that away. What order to reveal in is a question about the cards, so
 * it is decided where the cards are known and carried here rather than worked out again by
 * whatever is drawing it.
 *
 * @param from  which pack this was, so a screen can say what was opened
 * @param cards every card, in reveal order
 */
public record OpenedPack(String from, List<CardIdentity> cards) {

    public OpenedPack {
        from = from == null ? "" : from;
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    /** The last card, which is the one the ceremony is for. */
    public CardIdentity last() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("An empty pack has no last card");
        }
        return cards.get(cards.size() - 1);
    }

    /** The same pack with its cards in a different order, which is what sorting produces. */
    public OpenedPack inOrder(List<CardIdentity> ordered) {
        return new OpenedPack(from, new ArrayList<>(ordered));
    }
}
