package dev.gathering.core.trade;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Whether a struck trade can actually be honored, and what moves if it can.
 *
 * <p>Asked at the moment of the swap and not before, however carefully the table was filled
 * in. A card can be put up and then dropped, spent, or lost in lava while the other side is
 * still reading; agreeing to a trade is not a promise the cards will still be there.
 *
 * <p>All of it or none of it. A trade that handed over the half somebody could still cover
 * would be a trade where losing a card mid-deal is a way to take one - and the person on the
 * other side agreed to a whole table, not to whatever was left of it.
 *
 * <p>Pure.
 */
public final class TradeSettlement {

    private TradeSettlement() {
    }

    /** What is short on one side, and how much of it. */
    public record Missing(UUID who, CardIdentity card, int howMany) {
    }

    /** What changes hands: each side's cards, on their way across. */
    public record Settled(UUID left, UUID right, CardTally toLeft, CardTally toRight) {

        public int size() {
            return toLeft.total() + toRight.total();
        }
    }

    /**
     * The swap, or nothing where either side can no longer cover what they put up.
     *
     * @param leftHas  everything the left-hand person has to hand, right now
     * @param rightHas the same for the other
     */
    public static Optional<Settled> of(TradeTable table, CardTally leftHas, CardTally rightHas) {
        if (table == null || !table.isStruck() || !shortOf(table, leftHas, rightHas).isEmpty()) {
            return Optional.empty();
        }
        // Each side's cards go to the other. Named for where they are going rather than where
        // they came from, because that is what the caller is about to do with them.
        return Optional.of(new Settled(
                table.left(), table.right(), table.fromRight(), table.fromLeft()));
    }

    /**
     * Everything either side put up and can no longer produce.
     *
     * <p>Reported rather than merely counted, because "this trade cannot go through" is a
     * message somebody has to act on and the card that went missing is the whole of it.
     */
    public static List<Missing> shortOf(TradeTable table, CardTally leftHas, CardTally rightHas) {
        List<Missing> missing = new ArrayList<>();
        if (table == null) {
            return missing;
        }
        gather(table.left(), table.fromLeft(), leftHas, missing);
        gather(table.right(), table.fromRight(), rightHas, missing);
        return List.copyOf(missing);
    }

    private static void gather(UUID who, CardTally offered, CardTally has, List<Missing> missing) {
        CardTally holding = has == null ? CardTally.EMPTY : has;
        for (CardIdentity card : offered.cards()) {
            int short0 = offered.of(card) - holding.of(card);
            if (short0 > 0) {
                missing.add(new Missing(who, card, short0));
            }
        }
    }
}
