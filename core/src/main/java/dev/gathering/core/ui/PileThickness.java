package dev.gathering.core.ui;

/**
 * How tall a pile of cards stands on the table in the world, and where its edges show.
 * <p>A library of sixty on the block was one card lying on the felt: exactly what a library of
 * one looked like, and nothing like a deck. A real deck is a block of card stock you can see
 * the height of from across the room, and how tall it is tells you roughly how much is left
 * before anybody reads the number. The owner asked for piles that grow as they fill.
 * <p>Measured against the card, not in blocks, so a pile keeps its shape at every table size: a
 * sleeved Magic card is about 66 millimeters wide and half a millimetre thick, which makes a
 * sixty-card deck a little under half as tall as a card is wide. Drawn at that scale: thinner and
 * a small graveyard is a line, taller and a deck becomes a brick.
 * <p>Capped, because a hundred-card pile and a two-hundred-card one are both "a lot" and a
 * pile half a card tall stops reading as a deck and starts hiding the cards behind it.
 * <p>Pure, so the drawing and the pointer - which has to aim at the top of a pile, not at the
 * felt under it - ask the same numbers.
 */
public final class PileThickness {

    /** How thick one sleeved card is, as a fraction of the card's width: half a millimetre in 66. */
    public static final double PER_CARD = 0.5 / 66.0;

    /** The most cards a pile grows for. A Commander library is under this at the start. */
    public static final int TALLEST = 100;

    /** How many cards each band down a pile's side stands for, so its layers show without striping. */
    public static final int CARDS_PER_BAND = 5;

    private PileThickness() {
    }

    /**
     * How tall a pile of this many cards stands, in whatever units the card's width is given in.
     * Nothing for an empty pile, and a single card is one card thick - enough to have edges.
     */
    public static double of(int cards, double cardWidth) {
        if (cards <= 0 || cardWidth <= 0) {
            return 0.0;
        }
        return Math.min(cards, TALLEST) * PER_CARD * cardWidth;
    }

    /**
     * How many bands a pile's side is drawn in: a line every few cards, and none for a pile too
     * thin to hold two, where a line would be all there is and read as an outline.
     */
    public static int bands(int cards) {
        if (cards < CARDS_PER_BAND * 2) {
            return 1;
        }
        return (Math.min(cards, TALLEST) + CARDS_PER_BAND - 1) / CARDS_PER_BAND;
    }
}
