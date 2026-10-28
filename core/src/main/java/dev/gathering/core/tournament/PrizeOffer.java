package dev.gathering.core.tournament;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A prize put up for a tournament that does not exist yet: which place it is for, and which of the
 * host's hotbar slots holds it.
 * <p>A slot rather than the item itself, because there is nowhere to keep the item in between. The
 * host puts prizes up while filling in the create screen, and until they press Create there is no
 * event to hold anything - so what is written down is the promise, and the items are taken out of
 * the host's own hotbar at the moment the event opens. The slot is the host's, read on the server
 * from the host's own inventory, so a client cannot name somebody else's property.
 * <p>One slot is at most one prize. A stack promised to two places would be handed out twice, and a
 * host who pressed the same button twice by accident should end up with one prize rather than a
 * refusal.
 * <p>Pure.
 *
 * <p>And what was in that slot when the promise was made, by name. The slot alone was a promise the
 * host could not keep: leave the screen, put the booster away, pick up a pickaxe, come back and press
 * Create, and the pickaxe was taken and handed to the winner. The name is only ever compared with what
 * the server itself reads out of that slot, so a client naming something it does not have gets nothing.
 *
 * @param place the finishing place this is for, from one
 * @param slot  the hotbar slot holding it, from zero
 * @param item  what the host had there when they put it up, as a registry name; blank promises nothing
 *              and is taken as a slot whose contents were never seen
 */
public record PrizeOffer(int place, int slot, String item) {

    /** How long an item's registry name may be on the wire. */
    public static final int LONGEST_ITEM = 200;

    public PrizeOffer {
        item = item == null ? "" : item.trim();
        if (item.length() > LONGEST_ITEM) {
            item = item.substring(0, LONGEST_ITEM);
        }
    }

    /** Whether this promise still describes what is in the slot, by name. */
    public boolean stillHolds(String what) {
        return !item.isEmpty() && item.equals(what);
    }

    /** The lowest place a prize can be put up for. */
    public static final int LOWEST_PLACE = 16;

    /** How many slots a hotbar has. A prize comes from the hand, so it comes from one of these. */
    public static final int HOTBAR_SLOTS = 9;

    /** Whether this offer names a place and a slot that exist, and says what was in it. */
    public boolean isAcceptable() {
        return place >= 1 && place <= LOWEST_PLACE && slot >= 0 && slot < HOTBAR_SLOTS && !item.isEmpty();
    }

    /**
     * The offers a tournament can actually be created with, in the order they were put up: those
     * naming a real place and a real slot, one per slot, the first offer for a slot winning.
     * <p>The later offer loses rather than replacing the earlier one so that the list the host was
     * shown is the list that is taken - what they see on the screen is in this order too.
     */
    public static List<PrizeOffer> accepted(List<PrizeOffer> offered) {
        if (offered == null) {
            return List.of();
        }
        Set<Integer> slotsTaken = new LinkedHashSet<>();
        List<PrizeOffer> kept = new ArrayList<>();
        for (PrizeOffer offer : offered) {
            if (offer != null && offer.isAcceptable() && slotsTaken.add(offer.slot())) {
                kept.add(offer);
            }
        }
        return List.copyOf(kept);
    }
}
