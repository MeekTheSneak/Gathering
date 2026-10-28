package dev.gathering.core.ui;

/**
 * Where a replay's transport sits, and what a point on it means.
 * <p>Four buttons, a bar, and the count at the right-hand end, along the strip a game would
 * put its hand in. Moved out of the table screen, which drew it, answered its clicks and
 * scrubbed with it in three places that each worked the rectangles out for themselves. They
 * agree because there is now one answer; a hit test that disagreed with the drawing by a pixel
 * is a button that works everywhere except its own edge.
 * <p>Pure: given the strip and how wide the count is, every rectangle and every step follows.
 */
public record ReplayStrip(Rect strip, int countWidth) {

    /** How many transport buttons there are. */
    public static final int BUTTONS = 4;

    /** A button's side. */
    public static final int BUTTON = 18;

    /** Space between the buttons, and around the bar. */
    public static final int GAP = 4;

    /** How tall the bar is inside its strip. Thin: it is a ruler, not a trough. */
    public static final int BAR = 6;

    /** What a point on the strip is pointing at. */
    public enum Target {
        /** Back to the first step. */
        START,
        /** One step back. */
        BACK,
        /** Play, or pause. */
        PLAY_PAUSE,
        /** One step on. */
        ON,
        /** Somewhere along the bar - which step is {@link #stepUnder}. */
        BAR,
        /** Nothing on the strip. */
        NOTHING
    }

    /** Where the nth button is, from the left of the strip. */
    public Rect button(int index) {
        int top = strip.y() + (strip.height() - BUTTON) / 2;
        return new Rect(strip.x() + GAP + index * (BUTTON + GAP), top, BUTTON, BUTTON);
    }

    /** The bar between the buttons and the count. Never narrower than a pixel. */
    public Rect bar() {
        int left = button(BUTTONS - 1).right() + GAP * 2;
        int right = strip.right() - GAP * 2 - countWidth;
        return new Rect(left, strip.y() + (strip.height() - BAR) / 2,
                Math.max(1, right - left), BAR);
    }

    /** Where the count is written, from its left edge. */
    public int countX() {
        return bar().right() + GAP * 2;
    }

    /**
     * What a click here is on.
     * <p>The whole height of the strip answers for the bar, from its left end rightwards, not
     * the six pixels of bar itself: a ruler you have to hit exactly is a ruler nobody uses.
     */
    public Target at(int x, int y) {
        Target[] buttons = {Target.START, Target.BACK, Target.PLAY_PAUSE, Target.ON};
        for (int index = 0; index < BUTTONS; index++) {
            if (button(index).contains(x, y)) {
                return buttons[index];
            }
        }
        if (strip.contains(x, y) && x >= bar().x()) {
            return Target.BAR;
        }
        return Target.NOTHING;
    }

    /** Which step a point along the bar means. Clamped, so a drag off either end holds. */
    public int stepUnder(int x, int steps) {
        Rect bar = bar();
        if (steps <= 0 || bar.width() <= 1) {
            return 0;
        }
        double along = (x - bar.x()) / (double) bar.width();
        return (int) Math.round(Math.clamp(along, 0, 1) * steps);
    }

    /** How much of the bar is filled at this step. All of it when there are no steps. */
    public int filled(int step, int steps) {
        Rect bar = bar();
        return steps <= 0 ? bar.width()
                : (int) Math.round(bar.width() * (step / (double) steps));
    }
}
