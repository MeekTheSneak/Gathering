package dev.gathering.core.ui;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tidying a handful of cards into rows, worked out before anything moves.
 * <p>A Commander board after an hour is a mess, and tidying it by hand is forty drags that
 * everybody else at the table watches. This is the arithmetic for doing it in one act - but it
 * is only the arithmetic. What makes it safe to offer at all is that the answer is computed
 * here, shown to the player, and sent only if they say so: a board that rearranged itself the
 * moment somebody pressed a key would be a board nobody trusts to leave a card where they put
 * it.
 *
 * <p>Four rules, each of which is a way this could go wrong:
 *
 * <ul>
 *   <li><b>Only what was chosen.</b> Cards outside the selection are not planned and not
 *       moved. Tidying your own creatures must never shuffle somebody else's board, or the
 *       feature is a way to interfere with a game rather than to read one.
 *   <li><b>Attachments follow their host.</b> An aura or an equipment is drawn against the
 *       card it is on, so giving it a spot of its own would tear it off - visually, and then
 *       in the next person's understanding of the board. Attached cards are dropped from the
 *       plan; moving the host takes them with it.
 *   <li><b>Reading order is kept.</b> The cards are laid out in the order they are already in,
 *       top row first and left to right within it, so tidying rearranges the spacing and not
 *       the arrangement. Somebody who has grouped their board by color still has it grouped
 *       afterwards.
 *   <li><b>Rotation is not touched.</b> A card turned sideways is tapped, and a card turned
 *       because its owner likes it that way is theirs; neither is untidiness. Nothing here
 *       returns an angle, so nothing here can straighten one.
 * </ul>
 *
 * <p>Deterministic: the same cards in the same places give the same plan, every time and on
 * every machine. That is what lets a preview be trusted - the board that is drawn as a promise
 * is the board that arrives.
 * <p>Pure. It knows what a card id is and where a card is, and nothing else.
 */
public final class ArrangeSelection {

    /** One card being considered, as the board currently has it. */
    public record Card(CardInstanceId id, TablePosition at, CardInstanceId attachedTo) {

        public Card {
            if (id == null) {
                throw new IllegalArgumentException("A card being arranged needs an id");
            }
            if (at == null) {
                throw new IllegalArgumentException("A card being arranged needs a position");
            }
        }

        /** Whether this card is on another one, and so is not placed on its own. */
        public boolean isAttached() {
            return attachedTo != null;
        }
    }

    /** Where one card would go. Position only: see the note about rotation above. */
    public record Spot(CardInstanceId id, TablePosition to) {
    }

    /**
     * How far in from the edge of a mat the tidy grid sits.
     * <p>The same inset a spread board uses, so a tidied board and a dealt one agree about
     * where the usable part of a mat starts.
     */
    public static final int MARGIN = TableSpread.MARGIN;

    private ArrangeSelection() {
    }

    /**
     * Where each of these cards would go if they were tidied into rows.
     * <p>Never moves a card that is attached to another, never returns a spot for a card that
     * was not passed in, and returns exactly one spot per card it does place.
     *
     * @return the plan, in reading order; empty when there is nothing worth tidying
     */
    public static List<Spot> plan(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<Card> placeable = new ArrayList<>();
        for (Card card : cards) {
            if (card != null && !card.isAttached()) {
                placeable.add(card);
            }
        }
        if (placeable.size() < 2) {
            // One card is already tidy, and a plan that moves a single card to the corner of a
            // grid is a plan that surprised somebody.
            return List.of();
        }
        // Reading order as the board currently has it, so spacing changes and arrangement does
        // not. Ties broken by id so that two cards at exactly the same spot - which happens,
        // because cards may be dropped anywhere - still come out in a settled order rather
        // than whichever order the caller's list happened to be in.
        placeable.sort(Comparator
                .comparingInt((Card card) -> card.at().y())
                .thenComparingInt(card -> card.at().x())
                .thenComparing(card -> card.id().toString()));

        int columns = TableSpread.columnsFor(placeable.size());
        int rows = (placeable.size() + columns - 1) / columns;
        int usable = TablePosition.SPAN - MARGIN * 2;
        // Spread across the whole usable width whatever the count, so a tidied board fills its
        // mat rather than huddling in one corner. A single column or row sits in the middle of
        // its axis, because "evenly spaced" has no other sensible reading for one of them.
        List<Spot> plan = new ArrayList<>(placeable.size());
        for (int index = 0; index < placeable.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            plan.add(new Spot(placeable.get(index).id(), new TablePosition(
                    MARGIN + spread(column, columns, usable),
                    MARGIN + spread(row, rows, usable),
                    // The card keeps whatever angle it had. See the note on rotation.
                    placeable.get(index).at().rotation())));
        }
        return List.copyOf(plan);
    }

    /**
     * Whether a plan still describes the board it was made for.
     * <p>A preview is a promise about cards where they are now. Between making one and
     * agreeing to it, somebody can move a card to a graveyard - their own hand can, and at a
     * table of four so can somebody else - and the plan still names it, still says where it
     * should sit on the battlefield, and applying it would <b>put it back</b>. That is not
     * tidying; it is the convenience tool undoing a real decision, and an audit reproduced it:
     * zero cards in the graveyard afterwards, expected one.
     * <p>Nothing about the rules stops it, and nothing should: the mod moves cards where it is
     * asked. So the check belongs where the asking is decided.
     *
     * @param plan    what was promised
     * @param stillOn the cards currently on the battlefield this plan is about
     */
    public static boolean isStale(List<Spot> plan, java.util.Set<CardInstanceId> stillOn) {
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        if (stillOn == null) {
            return true;
        }
        for (Spot spot : plan) {
            if (!stillOn.contains(spot.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The part of a plan that still describes cards on the battlefield.
     * <p>The belt to {@link #isStale}'s braces. A board can change between the last frame that
     * checked and the moment a key is pressed, so the moves that actually go out are filtered
     * one more time - a card that has left is simply not moved, rather than dragged back.
     */
    public static List<Spot> stillStanding(
            List<Spot> plan, java.util.Set<CardInstanceId> stillOn) {
        if (plan == null || plan.isEmpty() || stillOn == null) {
            return List.of();
        }
        List<Spot> kept = new ArrayList<>();
        for (Spot spot : plan) {
            if (stillOn.contains(spot.id())) {
                kept.add(spot);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Where the {@code index}th of {@code howMany} sits along an axis {@code usable} long.
     * <p>One of them goes in the middle rather than at the start: a lone row pinned to the top
     * of a mat reads as a mistake, and the arithmetic for "evenly spaced" divides by zero.
     */
    private static int spread(int index, int howMany, int usable) {
        if (howMany <= 1) {
            return usable / 2;
        }
        return (int) Math.round((double) usable * index / (howMany - 1));
    }
}
