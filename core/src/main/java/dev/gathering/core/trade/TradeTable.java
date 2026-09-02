package dev.gathering.core.trade;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Two people and what they are putting up.
 * <p>Every trading system that ever shipped without this has the same scam in it: agree a
 * trade, wait for the other side to accept, swap the good card for a worse one, and take
 * theirs. So the rule that matters is not "both sides must agree" - it is <strong>both sides
 * must agree to the same thing at the same time</strong>. Any change to either offer clears
 * both agreements, and the swap only happens from a state where nothing has moved since both
 * were given.
 * <p>Which is why this is a value rather than a mutable table: every change makes a new one,
 * so "the offers when they agreed" and "the offers now" cannot quietly be the same object.
 * <p>Nothing here knows whether either side actually owns what they are offering. That is the
 * server's to check, and it has to check it again at the moment of the swap however carefully
 * this was filled in - a card can leave an inventory between offering it and agreeing.
 * <p>Pure.
 */
public record TradeTable(
        UUID left,
        UUID right,
        CardTally fromLeft,
        CardTally fromRight,
        boolean leftAgreed,
        boolean rightAgreed,
        Stage stage) {

    /** As many distinct cards as one side may put up. A trade is not a house move. */
    public static final int MOST_DISTINCT = 64;

    /** Where a trade has got to. */
    public enum Stage {
        /** Being put together. Offers can change and agreements come and go. */
        OPEN,
        /** Both sides agreed to what is on the table now. Nothing may change. */
        STRUCK,
        /** Somebody walked away, or it was already done. */
        CLOSED
    }

    public TradeTable {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        fromLeft = fromLeft == null ? CardTally.EMPTY : fromLeft;
        fromRight = fromRight == null ? CardTally.EMPTY : fromRight;
        stage = stage == null ? Stage.OPEN : stage;
    }

    /** A fresh table between two people, with nothing on it. */
    public static TradeTable between(UUID left, UUID right) {
        return new TradeTable(
                left, right, CardTally.EMPTY, CardTally.EMPTY, false, false, Stage.OPEN);
    }

    /** Whether this person is at this table at all. */
    public boolean seats(UUID who) {
        return left.equals(who) || right.equals(who);
    }

    /** What this person is putting up. */
    public CardTally offerFrom(UUID who) {
        return left.equals(who) ? fromLeft : right.equals(who) ? fromRight : CardTally.EMPTY;
    }

    /** Whether this person has agreed to what is on the table now. */
    public boolean hasAgreed(UUID who) {
        return left.equals(who) ? leftAgreed : right.equals(who) && rightAgreed;
    }

    /** The other person. */
    public Optional<UUID> across(UUID who) {
        return left.equals(who) ? Optional.of(right)
                : right.equals(who) ? Optional.of(left) : Optional.empty();
    }

    /**
     * Puts a card up, or takes one back down.
     * <p>Both agreements go with it, always, even when the change is somebody adding to their
     * own side. It does not matter whether a change is generous: what the other person agreed
     * to was a table, and this is a different table.
     *
     * @param howMany how many of this card are on the table now, not how many to add
     */
    public TradeTable putUp(UUID who, CardIdentity card, int howMany) {
        if (stage != Stage.OPEN || !seats(who) || card == null) {
            return this;
        }
        CardTally mine = offerFrom(who);
        int wanted = Math.max(0, howMany);
        if (wanted == mine.of(card)) {
            // Nothing changed, so nobody's agreement should be thrown away for it.
            return this;
        }
        if (wanted > 0 && !mine.has(card) && mine.distinct() >= MOST_DISTINCT) {
            return this;
        }
        CardTally changed = mine.take(card, mine.of(card)).left().plus(card, wanted);
        return withOffer(who, changed);
    }

    /** Takes everything back down, which is also a change and also clears both agreements. */
    public TradeTable clearOffer(UUID who) {
        if (stage != Stage.OPEN || !seats(who) || offerFrom(who).isEmpty()) {
            return this;
        }
        return withOffer(who, CardTally.EMPTY);
    }

    /**
     * Says this side is happy with the table as it stands.
     * <p>Once both are, the trade is struck and nothing can change - which is the whole point:
     * there is no window between the second agreement and the swap for anybody to reach into.
     */
    public TradeTable agree(UUID who) {
        if (stage != Stage.OPEN || !seats(who) || hasAgreed(who)) {
            return this;
        }
        boolean nowLeft = left.equals(who) || leftAgreed;
        boolean nowRight = right.equals(who) || rightAgreed;
        return new TradeTable(left, right, fromLeft, fromRight, nowLeft, nowRight,
                nowLeft && nowRight ? Stage.STRUCK : Stage.OPEN);
    }

    /** Takes an agreement back, which anybody may do until the other side gives theirs. */
    public TradeTable thinkAgain(UUID who) {
        if (stage != Stage.OPEN || !seats(who) || !hasAgreed(who)) {
            return this;
        }
        return new TradeTable(left, right, fromLeft, fromRight,
                leftAgreed && !left.equals(who), rightAgreed && !right.equals(who), Stage.OPEN);
    }

    /** Walks away. Anybody may, at any point before the swap. */
    public TradeTable close() {
        return stage == Stage.CLOSED
                ? this
                : new TradeTable(left, right, fromLeft, fromRight, false, false, Stage.CLOSED);
    }

    /** Whether the swap should happen now. */
    public boolean isStruck() {
        return stage == Stage.STRUCK;
    }

    /** Whether anything is on the table at all. */
    public boolean isEmpty() {
        return fromLeft.isEmpty() && fromRight.isEmpty();
    }

    /** How many cards would change hands. */
    public int size() {
        return fromLeft.total() + fromRight.total();
    }

    private TradeTable withOffer(UUID who, CardTally offer) {
        // Both agreements, not just the other side's. Somebody who changes their own offer
        // and stays agreed has agreed to a table nobody has seen.
        return left.equals(who)
                ? new TradeTable(left, right, offer, fromRight, false, false, Stage.OPEN)
                : new TradeTable(left, right, fromLeft, offer, false, false, Stage.OPEN);
    }
}
