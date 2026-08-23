package dev.gathering.core.game;

/**
 * The Commander-first zone set, one of each per seat.
 *
 * <p>Two properties drive everything else in the mod:
 *
 * <ul>
 *   <li><b>Hidden</b> zones hold cards whose identity the server never sends to anyone but
 *       their owner. Manipulating them is owner-locked, and that is the one thing the mod
 *       does say no to.</li>
 *   <li><b>Public</b> zones are open: in the paper-Magic tradition any seated player may
 *       move any card in one, because control-changing effects are core Magic and the event
 *       log - which attributes every action by name - is the honesty mechanism rather than a
 *       permission check.</li>
 * </ul>
 *
 * <p>There is no stack. The mod tracks no stack, enforces no priority, and has no opinion
 * about what is currently resolving; that lives in the players' heads exactly as it does in
 * paper.
 */
public enum Zone {

    /** Face-down pile. Everyone sees a count and nothing else, including its owner. */
    LIBRARY(Hidden.YES, Ordered.YES),

    /** The owner sees it in full; everyone else sees a count. */
    HAND(Hidden.YES, Ordered.NO),

    /** Public. A card here may sit on any seat's side of the table, which is how control changes. */
    BATTLEFIELD(Hidden.NO, Ordered.NO),

    /** Public and ordered: graveyard order is real information in Magic. */
    GRAVEYARD(Hidden.NO, Ordered.YES),

    /** Public, but individual cards here are often face down, which the facing handles. */
    EXILE(Hidden.NO, Ordered.NO),

    /** Public, and where commanders start, which is why they are naturally exempt from ante. */
    COMMAND(Hidden.NO, Ordered.NO);

    /** Named rather than boolean, because two bare booleans at a call site say nothing. */
    private enum Hidden { YES, NO }

    private enum Ordered { YES, NO }

    private final boolean hidden;
    private final boolean ordered;

    Zone(Hidden hidden, Ordered ordered) {
        this.hidden = hidden == Hidden.YES;
        this.ordered = ordered == Ordered.YES;
    }

    /**
     * Whether card identity in this zone is hidden from players other than the owner.
     *
     * <p>Note that a public zone can still hold face-down cards; facing is per card, this is
     * per zone. A card is visible only if both agree.
     */
    public boolean isHidden() {
        return hidden;
    }

    public boolean isPublic() {
        return !hidden;
    }

    /** Whether position within the zone is meaningful - top and bottom of a library. */
    public boolean isOrdered() {
        return ordered;
    }

    /**
     * Whether cards here sit on a surface rather than in a pile.
     *
     * <p>Only the battlefield. Everything else renders as a thin stack with a count, and a
     * stack has an order rather than a geometry - so only the battlefield gives its cards a
     * place and an angle, and only the battlefield accepts a drop at a chosen one.
     */
    public boolean isSurface() {
        return this == BATTLEFIELD;
    }

    /**
     * Whether a non-owner may manipulate cards here.
     *
     * <p>The mod never says no about rules. It does say no about reaching into someone's
     * hand or library, which is not a rule but the security property.
     */
    public boolean isOpenToAllSeats() {
        return !hidden;
    }

    /**
     * The zones that sit on the table as a stack of cards, in the order they are laid out.
     *
     * <p>One list, because two things draw this row and a third decides what a card dropped on
     * it lands in. When they were written out separately, the one that drew the zones and the
     * one that worked out which zone a point was over could in principle disagree - and a drop
     * that puts a card in the wrong zone is the kind of thing nobody reports, they just stop
     * dropping cards there.
     */
    public static final java.util.List<Zone> PILES =
            java.util.List.of(LIBRARY, GRAVEYARD, EXILE, COMMAND);
}
