package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;

/**
 * Where the table is being looked at from.
 *
 * <p>The whole screen is the felt now, so something has to say which part of the table is
 * on it: a point the view is centred on and how many pixels a table unit is worth. Everything
 * that draws a card or works out what the cursor is pointing at goes through here, which is
 * what keeps them agreeing.
 *
 * <p><b>Floating point here, deliberately, having refused it for positions.</b> A card's place
 * is state - it crosses the wire, the log and undo, and two clients disagreeing in the last
 * bit of a float drift apart over a long game. A camera is one player's view for one frame. It
 * is never sent anywhere, never stored, and never compared against anybody else's, so the
 * reason to avoid float does not apply and the arithmetic is nicer without it.
 *
 * @param centreX the table point in the middle of the screen, in {@link TablePosition} units
 * @param scale   screen pixels per table unit
 */
public record TableCamera(double centreX, double centreY, double scale) {

    /**
     * How wide a card is, as a fraction of the table.
     *
     * <p>Ten cards across. That is roughly what a real table holds in a row and it is what
     * makes a zoomed-out board readable rather than a mosaic.
     */
    public static final double CARD_WIDTH_UNITS = TablePosition.SPAN / 10.0;

    private static final double CARD_ASPECT = 488.0 / 680.0;

    /** How tall a card is drawn, in table units. */
    public static final double CARD_HEIGHT_UNITS = CARD_WIDTH_UNITS / CARD_ASPECT;

    /** A card at the size it stops being identifiable, and at the size it stops being useful. */
    private static final double MIN_CARD_PIXELS = 24.0;
    private static final double MAX_CARD_PIXELS = 260.0;

    public TableCamera {
        centreX = clampToTable(centreX);
        centreY = clampToTable(centreY);
        scale = clampScale(scale);
    }

    /**
     * A camera showing the whole table in a viewport of this size.
     *
     * <p>What a player gets the first time they sit down: everything, at whatever size
     * everything happens to be, rather than a close-up of a corner they have to find their way
     * out of.
     */
    public static TableCamera showingAll(int viewportWidth, int viewportHeight) {
        double fit = Math.min(
                (double) Math.max(1, viewportWidth) / TablePosition.SPAN,
                (double) Math.max(1, viewportHeight) / TablePosition.SPAN);
        return new TableCamera(TablePosition.SPAN / 2.0, TablePosition.SPAN / 2.0, fit);
    }

    // ------------------------------------------------------------- the maths

    public double toScreenX(double tableX, int viewportWidth) {
        return viewportWidth / 2.0 + (tableX - centreX) * scale;
    }

    public double toScreenY(double tableY, int viewportHeight) {
        return viewportHeight / 2.0 + (tableY - centreY) * scale;
    }

    public double toTableX(double screenX, int viewportWidth) {
        return centreX + (screenX - viewportWidth / 2.0) / scale;
    }

    public double toTableY(double screenY, int viewportHeight) {
        return centreY + (screenY - viewportHeight / 2.0) / scale;
    }

    /** Where a card at this position is drawn, as a rectangle on screen. */
    public Rect cardAt(TablePosition position, int viewportWidth, int viewportHeight) {
        return new Rect(
                (int) Math.round(toScreenX(position.x(), viewportWidth)),
                (int) Math.round(toScreenY(position.y(), viewportHeight)),
                cardWidthPixels(),
                cardHeightPixels());
    }

    public int cardWidthPixels() {
        return Math.max(1, (int) Math.round(CARD_WIDTH_UNITS * scale));
    }

    public int cardHeightPixels() {
        return Math.max(1, (int) Math.round(CARD_HEIGHT_UNITS * scale));
    }

    /**
     * The position a card's top-left corner would have if dropped at this screen point.
     *
     * <p>Clamped, not refused. The cursor spends plenty of time off the table - over the
     * surround, past an edge mid-drag - and a position that threw there would crash the client
     * for dragging a card a little too far.
     */
    public TablePosition positionAt(double screenX, double screenY, int viewportWidth, int viewportHeight) {
        return TablePosition.clamped(
                (int) Math.round(toTableX(screenX, viewportWidth)),
                (int) Math.round(toTableY(screenY, viewportHeight)));
    }

    // ------------------------------------------------------------- moving it

    /** Drags the view by a number of screen pixels, which is what a pan gesture produces. */
    public TableCamera pannedBy(double pixelsX, double pixelsY) {
        return new TableCamera(centreX - pixelsX / scale, centreY - pixelsY / scale, scale);
    }

    /**
     * Zooms about a point on screen, so whatever is under the cursor stays under it.
     *
     * <p>The thing every map and every table does, and the thing you notice immediately when
     * it is missing: zooming about the middle of the screen means the card you were looking at
     * slides away as you lean in on it.
     */
    public TableCamera zoomedAt(double factor, double screenX, double screenY, int width, int height) {
        double anchorX = toTableX(screenX, width);
        double anchorY = toTableY(screenY, height);
        double zoomed = clampScale(scale * factor);
        if (zoomed == scale) {
            return this;
        }
        // Solve for the centre that keeps the anchor under the same pixel at the new scale.
        return new TableCamera(
                anchorX - (screenX - width / 2.0) / zoomed,
                anchorY - (screenY - height / 2.0) / zoomed,
                zoomed);
    }

    public boolean isAtClosest() {
        return scale >= MAX_CARD_PIXELS / CARD_HEIGHT_UNITS;
    }

    public boolean isAtFurthest() {
        return scale <= MIN_CARD_PIXELS / CARD_HEIGHT_UNITS;
    }

    private static double clampScale(double wanted) {
        double lowest = MIN_CARD_PIXELS / CARD_HEIGHT_UNITS;
        double highest = MAX_CARD_PIXELS / CARD_HEIGHT_UNITS;
        return Math.max(lowest, Math.min(highest, wanted));
    }

    /**
     * Keeps the view over the table.
     *
     * <p>Panning off the edge is the one way to get lost at a table with no landmarks, and
     * "everything vanished" is indistinguishable from a bug.
     */
    private static double clampToTable(double wanted) {
        return Math.max(0, Math.min(TablePosition.SPAN, wanted));
    }
}
