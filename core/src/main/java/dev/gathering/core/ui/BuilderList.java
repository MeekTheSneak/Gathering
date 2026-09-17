package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The column of a deck builder that lists what has been picked, as lines with places.
 * <p>Several sections, each a heading and the rows under it, scrolled as one column - the
 * piles of the deck, and the sideboard at the foot of them. The screen used to walk the
 * sections itself, adding a row height per line and measuring the total as it drew, which
 * meant the scroll limit was only right after a frame had been drawn and only for the sections
 * that frame happened to have. A second section made that worse rather than longer: the
 * sideboard is at the bottom, so anything the height is short by is exactly what cannot be
 * scrolled to.
 * <p>So the arithmetic is here, where it can be checked at every window size and every mix of
 * sections rather than at the one the author was running.
 * <p>An empty section is not laid out at all, heading included: a heading with nothing under
 * it makes the list longer and says nothing.
 * <p>Coordinates are GUI-scaled screen units, origin top left. Pure.
 *
 * @param rowHeight how tall one line is, heading or row
 * @param top       the first line of the window, before scrolling
 * @param bottom    one past the last line the window can show
 */
public record BuilderList(int rowHeight, int top, int bottom) {

    /**
     * One line of the list and where it sits this frame.
     * <p>A heading carries the section's number and a row of {@code -1}, so a click on it can
     * be told from a click on a card without a second list to look in.
     *
     * @param section which section it belongs to, in the order they were given
     * @param row     which row of that section, or -1 for the section's heading
     * @param y       the top of the line, scrolling already taken off
     */
    public record Line(int section, int row, int y) {

        public boolean isHeading() {
            return row < 0;
        }
    }

    /** How tall one line is at the size this shipped at, for a screen reserving space. */
    public static final int ROW_HEIGHT = 11;

    /** How tall the whole list is: a line per heading, a line per row, empty sections left out. */
    public static int contentHeight(List<Integer> rowsPerSection, int rowHeight) {
        int lines = 0;
        for (Integer rows : rowsPerSection == null ? List.<Integer>of() : rowsPerSection) {
            int count = rows == null ? 0 : Math.max(0, rows);
            if (count > 0) {
                lines += count + 1;
            }
        }
        return lines * Math.max(1, rowHeight);
    }

    /**
     * Every line of the list at this scroll, in the order they are read.
     * <p>All of them, including the ones scrolled out of the window: a row that cannot be seen
     * is still a row whose place the list has to agree about, and {@link #shows} is what says
     * whether to draw it. Handing back only the visible ones would make the hit test and the
     * drawing two different lists of rows, which is how a click lands on the card above the
     * one under the pointer.
     */
    public List<Line> lines(List<Integer> rowsPerSection, int scroll) {
        List<Line> lines = new ArrayList<>();
        if (rowsPerSection == null) {
            return List.copyOf(lines);
        }
        int height = Math.max(1, rowHeight);
        int y = top - scroll;
        for (int section = 0; section < rowsPerSection.size(); section++) {
            Integer counted = rowsPerSection.get(section);
            int rows = counted == null ? 0 : Math.max(0, counted);
            if (rows == 0) {
                continue;
            }
            lines.add(new Line(section, -1, y));
            y += height;
            for (int row = 0; row < rows; row++) {
                lines.add(new Line(section, row, y));
                y += height;
            }
        }
        return List.copyOf(lines);
    }

    /**
     * How far down the list can be scrolled, which is never past its last line.
     * <p>Zero for a list that fits, so a short deck cannot be scrolled off the top of its own
     * column.
     */
    public int deepestScroll(List<Integer> rowsPerSection) {
        return Math.max(0, contentHeight(rowsPerSection, rowHeight) - height());
    }

    /** The same scroll, kept to a list that may have shrunk or a window that may have grown. */
    public int scrollWithin(int scroll, List<Integer> rowsPerSection) {
        return Math.max(0, Math.min(scroll, deepestScroll(rowsPerSection)));
    }

    /**
     * Whether a line at this y is wholly inside the window, and so drawn.
     * <p>Wholly, not partly. A line half in the window is a line drawn over the heading above
     * the column or over the curve below it, and both were happening: the check was against
     * the pane rather than against the list, so a scrolled row could be drawn across the
     * deck's own total.
     */
    public boolean shows(int y) {
        return y >= top && y + rowHeight <= bottom;
    }

    /** How tall the window is. Never negative, however small the screen got. */
    public int height() {
        return Math.max(0, bottom - top);
    }
}
