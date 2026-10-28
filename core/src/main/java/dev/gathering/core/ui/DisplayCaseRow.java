package dev.gathering.core.ui;

/**
 * Where the cards stand in a display case: a row of up to four across the front of the block.
 * <p>Worked out here rather than as numbers in the renderer, because the numbers were wrong in a way
 * nobody could see from the code. A card stood 0.34 of a block tall, which makes it 0.218 wide, and
 * they stood 0.21 apart - so each overlapped the next by a sliver, in the same plane, and the
 * overlap flickered. The owner saw them clipping into each other. A row that fits inside the glass
 * with a gap between every card is now a property that is checked.
 * <p>In blocks, across the case, with the middle of the block at 0.5. Pure.
 */
public final class DisplayCaseRow {

    /** How tall a card stands in the case, as a fraction of a block. It is a counter, not a cabinet. */
    public static final float TALL = 0.31f;

    /** How far apart the middles of neighboring cards stand. */
    public static final float APART = 0.215f;

    /** The most cards a case holds. */
    public static final int HOLDS = 4;

    /** A card's width for its height: printed at 2.5 by 3.5 inches, drawn 0.64 of its box wide. */
    public static final float WIDTH_PER_TALL = 0.64f;

    /** Where the inside of the glass is, either side: the panes stand a sixteenth in from the edge. */
    public static final float GLASS = 1f / 16f;

    private DisplayCaseRow() {
    }

    /** How wide one card is, in blocks. */
    public static float cardWidth() {
        return TALL * WIDTH_PER_TALL;
    }

    /**
     * Where the middle of card {@code at} of {@code count} stands, as an offset from the middle of the
     * block. Centered as a row however many there are, so two cards sit in the middle of the case
     * rather than at one end of a row of four gaps.
     */
    public static float offsetOf(int at, int count) {
        return -APART * (count - 1) / 2f + APART * at;
    }

    /** The gap between two neighboring cards, in blocks. Positive is a gap; negative, an overlap. */
    public static float gap() {
        return APART - cardWidth();
    }
}
