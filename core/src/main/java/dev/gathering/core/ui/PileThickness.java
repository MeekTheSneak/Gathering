package dev.gathering.core.ui;

/**
 * How tall a pile of cards stands on the table in the world, and where its edges show.
 * <p>A library of sixty on the block was one card lying on the felt: exactly what a library of
 * one looked like, and nothing like a deck. A real deck is a block of card stock you can see
 * the height of from across the room, and how tall it is tells you roughly how much is left
 * before anybody reads the number. The owner asked for piles that grow as they fill.
 * <p>Measured against the card, not in blocks, so a pile keeps its shape at every table size: a
 * sleeved Magic card is about 66 millimeters wide and seven tenths of a millimeter thick, which makes
 * a sixty-card deck about two thirds as tall as a card is wide. It was drawn at half a millimeter, an
 * unsleeved card, and the owner looking along a table could not tell a stack of four from one of
 * twelve: every stack is the same cards sleeved, so it is drawn as sleeved cards stack.
 * <p>Capped, because a hundred-card pile and a two-hundred-card one are both "a lot" and a
 * pile half a card tall stops reading as a deck and starts hiding the cards behind it.
 * <p>Pure, so the drawing and the pointer - which has to aim at the top of a pile, not at the
 * felt under it - ask the same numbers.
 */
public final class PileThickness {

    /** How thick one sleeved card is, as a fraction of the card's width: seven tenths of a millimeter in 66. */
    public static final double PER_CARD = 0.7 / 66.0;

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

    /** How many upright sides a pile has. */
    public static final int SIDES = 4;

    /**
     * The corners of one upright side of a pile centered on the origin, as {x, y, z} each, in the
     * order that faces them outward: counterclockwise seen from outside the pile.
     * <p>The order is the whole point. The world culls a face seen from behind, and the sides were
     * first wound the other way round - so the four walls facing the camera were dropped and the
     * inside of the far ones drawn through them, and a deck on the table looked hollow, as though
     * the cards under the top one were see-through.
     *
     * @param side 0 toward -z, 1 toward +z, 2 toward +x, 3 toward -x
     */
    public static double[][] sideCorners(int side, double halfWidth, double halfDepth, double bottom, double top) {
        double[] from;
        double[] to;
        switch (side) {
            case 0 -> {
                from = new double[] {halfWidth, -halfDepth};
                to = new double[] {-halfWidth, -halfDepth};
            }
            case 1 -> {
                from = new double[] {-halfWidth, halfDepth};
                to = new double[] {halfWidth, halfDepth};
            }
            case 2 -> {
                from = new double[] {halfWidth, halfDepth};
                to = new double[] {halfWidth, -halfDepth};
            }
            case 3 -> {
                from = new double[] {-halfWidth, -halfDepth};
                to = new double[] {-halfWidth, halfDepth};
            }
            default -> throw new IllegalArgumentException("a pile has no side " + side);
        }
        return new double[][] {
                {from[0], bottom, from[1]},
                {to[0], bottom, to[1]},
                {to[0], top, to[1]},
                {from[0], top, from[1]},
        };
    }

    /** Which way one side of a pile faces, as {x, z}. */
    public static int[] sideNormal(int side) {
        return switch (side) {
            case 0 -> new int[] {0, -1};
            case 1 -> new int[] {0, 1};
            case 2 -> new int[] {1, 0};
            case 3 -> new int[] {-1, 0};
            default -> throw new IllegalArgumentException("a pile has no side " + side);
        };
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
