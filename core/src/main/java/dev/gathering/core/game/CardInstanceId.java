package dev.gathering.core.game;

/**
 * One physical card in one session.
 *
 * <p>Distinct from {@link dev.gathering.core.card.CardIdentity}, which says *what* a card is.
 * Four copies of Lightning Bolt are one identity and four instances, because four copies is
 * four objects on a table and each one taps, moves, and gets exiled separately.
 *
 * <p>Instance ids are session-scoped and sequential. They are safe to show to any client
 * that is entitled to see the card at all - and the ones that are not entitled never receive
 * an instance id either, only an opaque {@link MarkerId}.
 */
public record CardInstanceId(int value) implements Comparable<CardInstanceId> {

    public CardInstanceId {
        if (value < 0) {
            throw new IllegalArgumentException("Card instance id must not be negative: " + value);
        }
    }

    public static CardInstanceId of(int value) {
        return new CardInstanceId(value);
    }

    @Override
    public int compareTo(CardInstanceId other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "card#" + value;
    }
}
