package dev.gathering.core.ui;

/**
 * Where every part of the life panel goes, at whatever size the window happens to be.
 * <p>The panel stacks two things: the life total, and one row per enemy commander to record
 * damage from. Commander damage lived under counters until the owner moved it here, and it
 * belongs here because it <em>is</em> life - three from somebody's commander is three off the
 * total in the row above it, and a player recording one wants to see the other move.
 * <p>Laid out the way {@link CountersLayout} is, and for the reason that one learned: the way
 * out is placed first, from the panel's bottom edge upward, and the rest shares what is left.
 * A panel laid out top down pushes its own Done button off a short screen, and a panel with no
 * way to close is worse than one with no commander grid.
 * <p>The life row never gives way. It is what the panel is for, and a life panel showing
 * everything except the life total would be a joke at the player's expense; the commander rows
 * window instead, and the heading says how many are out of sight.
 * <p>Both the widget building and the drawing read this, rather than each deriving the same
 * offsets from the same counts - which is how a row's minus button came to sit beside its
 * neighbour's name on the panel this one is modeled on.
 * <p>Coordinates are GUI-scaled screen units, origin top left.
 */
public record LifeLayout(
        Rect panel,
        int damageRows,
        Rect life,
        Rect damage,
        Rect done) {

    private static final int PANEL_WIDTH = 216;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;

    /** How tall one row of anything is, gap included. */
    public static int step() {
        return ROW + GAP;
    }

    public static int rowHeight() {
        return ROW;
    }

    public static int gap() {
        return GAP;
    }

    public static int margin() {
        return MARGIN;
    }

    public static int panelWidth() {
        return PANEL_WIDTH;
    }

    /**
     * @param commanders how many enemy commanders there are to record damage from
     */
    public static LifeLayout of(int screenWidth, int screenHeight, int commanders) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);
        int wantDamage = Math.max(0, commanders);

        // The panel is as tall as its content wants, up to the screen. Beyond that the
        // commander rows give way rather than the panel growing past the edge.
        int natural = MARGIN * 2 + ROW + body(wantDamage) + GAP * 2 + ROW;
        int tall = Math.min(natural, height - MARGIN * 2);
        Rect panel = new Rect((width - PANEL_WIDTH) / 2, Math.max(MARGIN, (height - tall) / 2),
                PANEL_WIDTH, Math.max(ROW, tall));

        // Placed first, from the bottom up, because it is the panel's only way out.
        Rect done = new Rect(panel.x() + MARGIN, panel.bottom() - MARGIN - ROW,
                PANEL_WIDTH - MARGIN * 2, ROW);

        int top = panel.y() + MARGIN + ROW;
        int room = Math.max(0, done.y() - GAP * 2 - top);

        int damageRows = wantDamage;
        // Down to one row, never to none: a table fielding commanders and showing no way to
        // record their damage is the panel failing at half of what it is for. One row plus a
        // heading saying how many are missing is still an answer.
        while (body(damageRows) > room && damageRows > 1) {
            damageRows--;
        }

        int content = PANEL_WIDTH - MARGIN * 2;
        Rect life = new Rect(panel.x() + MARGIN, top, content, ROW);
        Rect damage = damageRows == 0
                ? Rect.NONE
                : new Rect(panel.x() + MARGIN, top + ROW + GAP * 2, content,
                        ROW + damageRows * step() - GAP);

        return new LifeLayout(panel, damageRows, life, damage, done);
    }

    /** How tall everything between the title and the Done button is, at this row count. */
    private static int body(int damageRows) {
        return ROW + (damageRows == 0 ? 0 : GAP * 2 + ROW + damageRows * step() - GAP);
    }

    /** Where the nth commander-damage row goes. The heading is on the line above the first. */
    public Rect damageRow(int index) {
        return damage.isEmpty() || index < 0 || index >= damageRows
                ? Rect.NONE
                : new Rect(damage.x(), damage.y() + ROW + index * step(), damage.width(), ROW);
    }
}
