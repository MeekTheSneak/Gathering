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
 * @param place the finishing place this is for, from one
 * @param slot  the hotbar slot holding it, from zero
 */
public record PrizeOffer(int place, int slot) {

    /** The lowest place a prize can be put up for. */
    public static final int LOWEST_PLACE = 16;

    /** How many slots a hotbar has. A prize comes from the hand, so it comes from one of these. */
    public static final int HOTBAR_SLOTS = 9;

    /** Whether this offer names a place and a slot that exist. */
    public boolean isAcceptable() {
        return place >= 1 && place <= LOWEST_PLACE && slot >= 0 && slot < HOTBAR_SLOTS;
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
