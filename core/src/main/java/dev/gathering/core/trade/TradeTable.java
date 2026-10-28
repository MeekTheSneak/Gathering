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
        UUID id,
        UUID left,
        UUID right,
        CardTally fromLeft,
        CardTally fromRight,
        boolean leftAgreed,
        boolean rightAgreed,
        Stage stage,
        int revision) {

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
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        fromLeft = fromLeft == null ? CardTally.EMPTY : fromLeft;
        fromRight = fromRight == null ? CardTally.EMPTY : fromRight;
        stage = stage == null ? Stage.OPEN : stage;
        revision = Math.max(0, revision);
    }

    /**
     * A fresh table between two people, with nothing on it and an identity of its own.
     * <p>The identity is the other half of what an agreement has to name. The revision alone
     * says which <em>terms</em> were agreed to, and that closes the race inside one trade -
     * but every table starts at revision zero, so an agreement in flight when a trade closes
     * arrives at the next trade between the same two people naming a revision that exists
     * there too. An audit struck a second trade, on different terms including an empty offer
     * from one side, with an agreement sent about the first.
     * <p>Random rather than counted, because a counter is guessable and a guessable one lets
     * a client name a trade it was never shown. This is the one place in the mod that wants
     * an unguessable value rather than a reproducible one, so it is {@link UUID#randomUUID()}
     * and not the level's own generator - a shuffle has to replay from a seed and this must
     * never be predictable.
     */
    public static TradeTable between(UUID left, UUID right) {
        return new TradeTable(UUID.randomUUID(),
                left, right, CardTally.EMPTY, CardTally.EMPTY, false, false, Stage.OPEN, 0);
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
     * Says this side is happy with the table as it stands - the table they were looking at.
     * <p>Once both are, the trade is struck and nothing can change, which is the whole point:
     * there is no window between the second agreement and the swap for anybody to reach into.
     * <p>{@code seenRevision} is which table that was. Every change to an offer moves the
     * revision on, so an agreement is a statement about a particular set of terms rather than
     * about whatever is on the table by the time the packet lands. An audit reproduced the
     * difference: one side clicked agree, the packet was slow, the other side took their card
     * back and agreed, and the slow packet struck a trade whose terms its sender had never
     * seen - one card for nothing. Clearing the offers reset both agreements, which is why it
     * looked safe; what it could not do is reach the agreement already in flight.
     *
     * <p>{@code seenTable} is which trade. A revision on its own is not enough: every table
     * starts at zero, so an agreement left over from a trade that has closed names a revision
     * the next trade between the same two people also has. An audit struck a second trade -
     * different terms, one side offering nothing - with an agreement sent about the first.
     *
     * @param seenTable the identity of the trade the agreeing player was looking at
     * @param seenRevision the revision the agreeing player was shown, from the view they read
     */
    public TradeTable agree(UUID who, UUID seenTable, int seenRevision) {
        if (stage != Stage.OPEN || !seats(who) || hasAgreed(who)
                || !isStillShowing(seenTable, seenRevision)) {
            return this;
        }
        boolean nowLeft = left.equals(who) || leftAgreed;
        boolean nowRight = right.equals(who) || rightAgreed;
        return new TradeTable(id, left, right, fromLeft, fromRight, nowLeft, nowRight,
                nowLeft && nowRight ? Stage.STRUCK : Stage.OPEN, revision);
    }

    /**
     * Whether this is the table somebody agreeing was actually looking at.
     * <p>Both halves: the same trade, and the same terms within it. Either alone is not
     * enough - the revision without the identity lets an agreement from a closed trade strike
     * the next one, and the identity without the revision is the race this started as.
     */
    public boolean isStillShowing(UUID seenTable, int seenRevision) {
        return id.equals(seenTable) && seenRevision == revision;
    }

    /** Takes an agreement back, which anybody may do until the other side gives theirs. */
    public TradeTable thinkAgain(UUID who) {
        if (stage != Stage.OPEN || !seats(who) || !hasAgreed(who)) {
            return this;
        }
        return new TradeTable(id, left, right, fromLeft, fromRight,
                leftAgreed && !left.equals(who), rightAgreed && !right.equals(who), Stage.OPEN,
                revision);
    }

    /** Walks away. Anybody may, at any point before the swap. */
    public TradeTable close() {
        return stage == Stage.CLOSED
                ? this
                : new TradeTable(id, left, right, fromLeft, fromRight, false, false, Stage.CLOSED,
                        revision);
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
        //
        // And the revision moves on, which is what an agreement already in flight is checked
        // against: resetting the flags cannot reach a packet that has already been sent.
        return left.equals(who)
                ? new TradeTable(id, left, right, offer, fromRight, false, false, Stage.OPEN, revision + 1)
                : new TradeTable(id, left, right, fromLeft, offer, false, false, Stage.OPEN, revision + 1);
    }
}
