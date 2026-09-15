package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything on the how-to-play screen goes: the panel, the list of topics down its side,
 * the page beside them, and Done.
 * <p>From the window, the control size, the text size and how wide the longest topic's name is,
 * the way the tournament and settings screens are laid out - so the guide grows with the sizes a
 * player asked for, and in a window too narrow for a column of topics beside the page, the topics
 * go across the top instead of squeezing the page to nothing.
 * <p>Pure, so it is tested at every size and GUI scale rather than looked at in one.
 */
public final class GuideLayout {

    public static final int MARGIN = 8;
    private static final int BASE_ROW = 18;
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 300;
    /** The narrowest a page may be before the topics move above it. */
    private static final int LEAST_PAGE = 220;

    private final Rect panel;
    private final List<Rect> topics;
    private final Rect page;
    private final Rect done;
    private final int row;
    private final int gap;
    private final boolean across;
    private final Rect previous;
    private final Rect current;
    private final Rect next;

    private GuideLayout(Rect panel, List<Rect> topics, Rect page, Rect done, int row, int gap, boolean across,
            Rect previous, Rect current, Rect next) {
        this.panel = panel;
        this.topics = List.copyOf(topics);
        this.page = page;
        this.done = done;
        this.row = row;
        this.gap = gap;
        this.across = across;
        this.previous = previous;
        this.current = current;
        this.next = next;
    }

    /**
     * @param topicCount  how many topics there are
     * @param widestTopic how wide the longest topic's name is drawn, at the text size asked
     * @param doneWidth   how wide Done's label is drawn, at the text size asked
     */
    public static GuideLayout of(int width, int height, int controlPercent, float textScale, int fontLine,
            int topicCount, int widestTopic, int doneWidth) {
        float controls = InterfaceScale.asFraction(controlPercent);
        int row = InterfaceScale.rowHeightFor(BASE_ROW, fontLine, controlPercent, textScale, BASE_ROW - fontLine);
        int gap = Math.max(2, Math.round(3 * controls));
        float grow = Math.max(1f, Math.max(controls, textScale));
        int panelWidth = Math.max(1, Math.min(Math.round(PANEL_WIDTH * grow), width - 8));
        int panelHeight = Math.max(1, Math.min(Math.round(PANEL_HEIGHT * grow), height - 4));
        Rect panel = new Rect((width - panelWidth) / 2, Math.max(2, (height - panelHeight) / 2), panelWidth, panelHeight);

        int top = panel.y() + MARGIN + row;
        int bottomRow = panel.bottom() - MARGIN - row;
        int doneWide = Math.min(panel.width() - MARGIN * 2, Math.max(Math.round(70 * controls), doneWidth + 12));
        Rect done = new Rect(panel.right() - MARGIN - doneWide, bottomRow, doneWide, row);

        int column = Math.max(Math.round(90 * controls), widestTopic + 12);
        int inside = panel.width() - MARGIN * 2;
        // Across the top when a column beside the page would leave the page too narrow - or would
        // itself run past Done.
        boolean across = inside - column - MARGIN < LEAST_PAGE
                || top + topicCount * (row + gap) - gap > bottomRow - gap;
        List<Rect> topics = new ArrayList<>();
        Rect page;
        if (!across) {
            for (int index = 0; index < topicCount; index++) {
                topics.add(new Rect(panel.x() + MARGIN, top + index * (row + gap), column, row));
            }
            int pageX = panel.x() + MARGIN + column + MARGIN;
            page = new Rect(pageX, top, panel.right() - MARGIN - pageX, Math.max(1, bottomRow - gap - top));
        } else {
            // A narrow window: the topics in rows across the top, as many to a row as fit.
            int perRow = Math.max(1, Math.min(topicCount, (inside + gap) / (column + gap)));
            int each = (inside - gap * (perRow - 1)) / perRow;
            int rows = (topicCount + perRow - 1) / perRow;
            for (int index = 0; index < topicCount; index++) {
                topics.add(new Rect(panel.x() + MARGIN + index % perRow * (each + gap),
                        top + index / perRow * (row + gap), each, row));
            }
            int pageTop = top + rows * (row + gap) + gap;
            if (pageTop + row * 3 > bottomRow) {
                // Not even that: rows of topics would leave no page. One topic at a time, with a
                // step either way - the page is what the screen is for.
                int arrow = Math.max(row, Math.round(24 * controls));
                Rect previous = new Rect(panel.x() + MARGIN, top, arrow, row);
                Rect next = new Rect(panel.right() - MARGIN - arrow, top, arrow, row);
                Rect current = new Rect(previous.right() + gap, top, Math.max(1, next.x() - gap - previous.right() - gap), row);
                int switcherPage = top + row + gap * 2;
                return new GuideLayout(panel, List.of(), new Rect(panel.x() + MARGIN, switcherPage, inside,
                        Math.max(1, bottomRow - gap - switcherPage)), done, row, gap, true, previous, current, next);
            }
            page = new Rect(panel.x() + MARGIN, pageTop, inside, Math.max(1, bottomRow - gap - pageTop));
        }
        return new GuideLayout(panel, topics, page, done, row, gap, across, Rect.NONE, Rect.NONE, Rect.NONE);
    }

    /**
     * Whether there is room for only one topic at a time: then {@link #topics()} is empty and the
     * topic is chosen with {@link #previous()} and {@link #next()} either side of {@link #current()}.
     */
    public boolean oneAtATime() {
        return topics.isEmpty();
    }

    public Rect previous() {
        return previous;
    }

    public Rect current() {
        return current;
    }

    public Rect next() {
        return next;
    }

    public Rect panel() {
        return panel;
    }

    /** Where each topic's button goes, in the order the topics were counted. */
    public List<Rect> topics() {
        return topics;
    }

    /** Where the page is written, and scrolled. */
    public Rect page() {
        return page;
    }

    public Rect done() {
        return done;
    }

    public int row() {
        return row;
    }

    public int gap() {
        return gap;
    }

    /** Where the title goes: in the top row of the panel. */
    public int titleY() {
        return panel.y() + MARGIN / 2 + (row - 9) / 2;
    }

    /** Whether the topics went across the top because the window was too narrow for a column. */
    public boolean topicsAcross() {
        return across;
    }
}
