package dev.gathering.core.ui;

/**
 * How to lay a pack of cards out in a box so that every one of them is on screen.
 * <p>A draft pack is a comparison. You are not reading fifteen cards one at a time, you are
 * looking at all of them at once and deciding which is best - so a pack that scrolls is a
 * pack you cannot draft from, and a pack whose last row is under the edge of its panel is
 * worse than that: those cards cannot be clicked at all.
 * <p>So the cards shrink rather than the pack overflowing. There is a ceiling on how large
 * they get, because a four-card pack drawn to fill a window is silly, but no floor other than
 * being visible - a small window gets small cards, and Alt still reads any of them.
 * <p>Pure, so the arithmetic can be checked against sizes nobody thought to open the game at.
 */
public record PackLayout(int columns, int rows, int cardWidth, int cardHeight) {

    /** Below this a card is a smudge, so it is where shrinking stops and the box gives up. */
    public static final int SMALLEST_CARD = 12;

    /**
     * The largest cards that fit this many in this room, and how they are arranged.
     * <p>Every column count is tried rather than guessed at from the aspect ratio. There are
     * at most as many as there are cards, the arithmetic is a handful of divisions, and the
     * guess is wrong exactly when the box is an awkward shape - which is most windows.
     *
     * @param mostTall the ceiling on a card's height, so a small pack is not blown up
     * @return the arrangement, or one card wide at the smallest size if nothing fits
     */
    public static PackLayout fit(int cards, int roomWidth, int roomHeight, int gap, int mostTall) {
        int count = Math.max(1, cards);
        PackLayout best = null;
        for (int columns = 1; columns <= count; columns++) {
            int rows = (count + columns - 1) / columns;
            // What each cell may be, from each direction, and the card is the smaller answer.
            int cellWidth = (roomWidth - (columns - 1) * gap) / columns;
            int cellHeight = (roomHeight - (rows - 1) * gap) / rows;
            int height = Math.min(mostTall, Math.min(cellHeight, CardShape.heightFor(cellWidth)));
            if (height < SMALLEST_CARD) {
                continue;
            }
            PackLayout candidate = new PackLayout(
                    columns, rows, Math.max(1, CardShape.widthFor(height)), height);
            // Taller wins; on a tie the squarer arrangement does, because two rows of five
            // read as a pack and five rows of two read as a list.
            if (best == null
                    || candidate.cardHeight() > best.cardHeight()
                    || (candidate.cardHeight() == best.cardHeight()
                            && Math.abs(candidate.columns() - candidate.rows())
                                    < Math.abs(best.columns() - best.rows()))) {
                best = candidate;
            }
        }
        if (best != null) {
            return best;
        }
        // Nothing fits at a readable size. Drawn small rather than not at all: a box with
        // cards too small to read is a box somebody can resize, and an empty one is a fault.
        int rows = count;
        return new PackLayout(1, rows, Math.max(1, CardShape.widthFor(SMALLEST_CARD)), SMALLEST_CARD);
    }

    /** How wide the whole grid comes out, gaps included. */
    public int width(int gap) {
        return columns * cardWidth + (columns - 1) * gap;
    }

    /** And how tall. */
    public int height(int gap) {
        return rows * cardHeight + (rows - 1) * gap;
    }
}
