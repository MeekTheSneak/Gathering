package dev.gathering.core.game;

/**
 * The Commander-first zone set, one of each per seat.
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
    COMMAND(Hidden.NO, Ordered.NO),

    /**
     * The second command slot, for the deck that has two commanders.
     * <p>Partners, backgrounds and a Doctor's companion are all one deck with two cards that
     * start in the command zone, and each of them is cast and re-cast on its own tax. One
     * pile holding both made those two cards a stack of two with a single number under it,
     * which is the one thing about a command zone a player actually has to read.
     * <p>Drawn whether or not it is used, like every other zone: a slot that only appears
     * once something is in it is a slot nobody can drop a card on.
     */
    COMMAND_TWO(Hidden.NO, Ordered.NO);

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
     * <p>Note that a public zone can still hold face-down cards; facing is per card, this is
     * per zone. A card is visible only if both agree.
     */
    public boolean isHidden() {
        return hidden;
    }

    public boolean isPublic() {
        return !hidden;
    }

    /**
     * Whether cards here sit on a surface rather than in a pile.
     * <p>Only the battlefield. Everything else renders as a thin stack with a count, and a
     * stack has an order rather than a geometry - so only the battlefield gives its cards a
     * place and an angle, and only the battlefield accepts a drop at a chosen one.
     */
    public boolean isSurface() {
        return this == BATTLEFIELD;
    }

    /**
     * The zones that sit on the table as a stack of cards, nearest their own player first.
     * <p>One list, because two things draw this column and a third decides what a card dropped
     * on it lands in. When they were written out separately, the one that drew the zones and
     * the one that worked out which zone a point was over could in principle disagree - and a
     * drop that puts a card in the wrong zone is the kind of thing nobody reports, they just
     * stop dropping cards there.
     * <p>Graveyard nearest, then library, then exile, then the two command slots furthest
     * away and set apart from the other three. That is the order the tables people already
     * play on use, and it is the right order for the reason it is theirs: the graveyard is
     * the zone a hand reaches for most often and a command slot is one it touches twice a
     * game.
     */
    public static final java.util.List<Zone> PILES =
            java.util.List.of(GRAVEYARD, LIBRARY, EXILE, COMMAND, COMMAND_TWO);

    /** The command slots, in the order a deck's commanders are dealt into them. */
    public static final java.util.List<Zone> COMMAND_SLOTS = java.util.List.of(COMMAND, COMMAND_TWO);

    /** Whether this zone is one of the command slots, which several rules ask together. */
    public boolean isCommandSlot() {
        return this == COMMAND || this == COMMAND_TWO;
    }

    /**
     * How many of those a table without a command zone lays out.
     * <p>The command slots are last precisely so that a format with no commanders can leave
     * them off by drawing fewer. An empty box labeled with a zone the format does not have
     * is a question every player asks once and nobody asks twice.
     */
    public static final int PILES_WITHOUT_A_COMMAND_ZONE = PILES.size() - COMMAND_SLOTS.size();

    /** How many zones a table's column holds, which is the one place that decides. */
    public static int pilesFor(boolean commandZone) {
        return commandZone ? PILES.size() : PILES_WITHOUT_A_COMMAND_ZONE;
    }
}
