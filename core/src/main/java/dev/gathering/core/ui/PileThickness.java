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

    /** How many corners a side has. */
    public static final int CORNERS = 4;

    /**
     * Where one corner of one upright side of a pile centered on the origin is across the pile, in x;
     * {@link #cornerZ} says where in z, and {@link #atTheTop} how high. Corners 0 to 3 go round the
     * side in the order that faces it outward - counterclockwise seen from outside the pile: along the
     * bottom from its first end to its other, then up, then back along the top.
     * <p>The order is the whole point. The world culls a face seen from behind, and the sides were
     * first wound the other way round - so the four walls facing the camera were dropped and the
     * inside of the far ones drawn through them, and a deck on the table looked hollow, as though
     * the cards under the top one were see-through.
     * <p>A number at a time because this is drawn for every side of every band of every pile at every
     * table in sight, every frame: handed over as arrays of corners it was thirteen small arrays a side.
     *
     * @param side 0 toward -z, 1 toward +z, 2 toward +x, 3 toward -x
     */
    public static double cornerX(int side, int corner, double halfWidth, double halfDepth) {
        boolean firstEnd = corner == 0 || corner == CORNERS - 1;
        return switch (side) {
            case 0 -> firstEnd ? halfWidth : -halfWidth;
            case 1 -> firstEnd ? -halfWidth : halfWidth;
            case 2 -> halfWidth;
            case 3 -> -halfWidth;
            default -> throw new IllegalArgumentException("a pile has no side " + side);
        };
    }

    /** And in z. */
    public static double cornerZ(int side, int corner, double halfWidth, double halfDepth) {
        boolean firstEnd = corner == 0 || corner == CORNERS - 1;
        return switch (side) {
            case 0 -> -halfDepth;
            case 1 -> halfDepth;
            case 2 -> firstEnd ? halfDepth : -halfDepth;
            case 3 -> firstEnd ? -halfDepth : halfDepth;
            default -> throw new IllegalArgumentException("a pile has no side " + side);
        };
    }

    /** Whether this corner of a side is at its top: the last two, wound up from the two along the bottom. */
    public static boolean atTheTop(int corner) {
        return corner >= 2;
    }

    /** Which way one side of a pile faces, across, as a whole number. */
    public static int normalX(int side) {
        return switch (side) {
            case 0, 1 -> 0;
            case 2 -> 1;
            case 3 -> -1;
            default -> throw new IllegalArgumentException("a pile has no side " + side);
        };
    }

    /** And down. */
    public static int normalZ(int side) {
        return switch (side) {
            case 0 -> -1;
            case 1 -> 1;
            case 2, 3 -> 0;
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
