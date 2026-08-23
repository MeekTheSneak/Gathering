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
 * @param spanX   how wide the surface is, so the view cannot be panned off the end of it
 * @param spanY   and how deep
 */
public record TableCamera(double centreX, double centreY, double scale, int spanX, int spanY) {

    /** A card's footprint, which is the surface's business rather than the camera's. */
    public static final double CARD_WIDTH_UNITS = TableSurface.CARD_WIDTH_UNITS;

    public static final double CARD_HEIGHT_UNITS = TableSurface.CARD_HEIGHT_UNITS;

    /** A card at the size it stops being identifiable, and at the size it stops being useful. */
    private static final double MIN_CARD_PIXELS = 24.0;
    private static final double MAX_CARD_PIXELS = 260.0;

    public TableCamera {
        spanX = Math.max(1, spanX);
        spanY = Math.max(1, spanY);
        centreX = Math.max(0, Math.min(spanX, centreX));
        centreY = Math.max(0, Math.min(spanY, centreY));
        scale = clampScale(scale);
    }

    /**
     * A camera over a single table, which is the shape most of them are.
     *
     * <p>The surface used to be assumed square and one table across, and the assumption was
     * spread through the arithmetic rather than stated. It is stated now, and this is the
     * common case rather than the only one.
     */
    public TableCamera(double centreX, double centreY, double scale) {
        this(centreX, centreY, scale, TablePosition.SPAN, TablePosition.SPAN);
    }

    /**
     * A camera showing the whole table in a viewport of this size.
     *
     * <p>What a player gets the first time they sit down: everything, at whatever size
     * everything happens to be, rather than a close-up of a corner they have to find their way
     * out of.
     */
    public static TableCamera showingAll(int viewportWidth, int viewportHeight) {
        return showingAll(TablePosition.SPAN, TablePosition.SPAN, viewportWidth, viewportHeight);
    }

    /**
     * A camera showing the whole of a surface this size in a viewport that size.
     *
     * <p>The surface's own proportions, not a square: two tables pushed together are twice as
     * wide as they are deep, and fitting that to a square left the board in a box in the
     * middle of the screen with the rest of the window empty.
     */
    public static TableCamera showingAll(
            int surfaceWidth, int surfaceHeight, int viewportWidth, int viewportHeight) {
        int across = Math.max(1, surfaceWidth);
        int down = Math.max(1, surfaceHeight);
        double fit = Math.min(
                (double) Math.max(1, viewportWidth) / across,
                (double) Math.max(1, viewportHeight) / down);
        return new TableCamera(across / 2.0, down / 2.0, fit, across, down);
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
        return new TableCamera(
                centreX - pixelsX / scale, centreY - pixelsY / scale, scale, spanX, spanY);
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
                zoomed, spanX, spanY);
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

}
