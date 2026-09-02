package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;

/**
 * Where the table is being looked at from.
 * <p>The whole screen is the felt now, so something has to say which part of the table is
 * on it: a point the view is centered on and how many pixels a table unit is worth. Everything
 * that draws a card or works out what the cursor is pointing at goes through here, which is
 * what keeps them agreeing.
 * <p><b>Floating point here, deliberately, having refused it for positions.</b> A card's place
 * is state - it crosses the wire, the log and undo, and two clients disagreeing in the last
 * bit of a float drift apart over a long game. A camera is one player's view for one frame. It
 * is never sent anywhere, never stored, and never compared against anybody else's, so the
 * reason to avoid float does not apply and the arithmetic is nicer without it.
 *
 * @param centerX the table point in the middle of the screen, in {@link TablePosition} units
 * @param scale   screen pixels per table unit
 * @param spanX   how wide the surface is, so the view cannot be panned off the end of it
 * @param spanY   and how deep
 * @param turned  whether the viewer is sitting at the far side of it - see below
 */
public record TableCamera(
        double centerX, double centerY, double scale, int spanX, int spanY, boolean turned) {

    /**
     * A card on a table with one mat on it, which is what the zoom limits are stated against.
     * <p>Not the size anything is drawn at. A card is a share of its own mat and a mat is a
     * share of the table, so how big a card really is depends on whose board it is on -
     * {@link TableSurface#cardWidthOn} is the only answer to that, and it is the one both
     * views use. This is a reference length, so that "zoomed out far enough that a card is
     * twenty pixels" means something without naming a seat.
     */
    private static final double REFERENCE_CARD_HEIGHT = TableSurface.CARD_HEIGHT_UNITS;

    /** A card at the size it stops being identifiable, and at the size it stops being useful. */
    private static final double MIN_CARD_PIXELS = 24.0;
    private static final double MAX_CARD_PIXELS = 260.0;

    /**
     * The same two limits, for the view that measures its zoom in blocks rather than pixels.
     * <p>How far in and out a board may go is a statement about reading cards, not about
     * cameras, so both views have to reach the same answer or the same key gives two
     * different boards. The in-world one had its own pair of numbers and framed the whole
     * table a fifth smaller because of it.
     */
    public static double smallestCardPixels() {
        return MIN_CARD_PIXELS;
    }

    public static double largestCardPixels() {
        return MAX_CARD_PIXELS;
    }

    /**
     * <b>Turned</b> is the difference between watching a game and playing in one.
     * <p>A surface is laid out once, against the table: north is north for everybody. Half the
     * players are sitting at the north edge, and from that chair the table is the other way
     * up - their own mat is the near one, their own right hand is the table's west. Drawing
     * the surface the same way for both of them gives one player somebody else's view of their
     * own board: their zones along the far edge, their opponent behind them.
     * <p>So the whole mapping between the surface and the screen turns half of the time, and
     * it turns <em>here</em> - once, where drawing and pointing both go through it - rather
     * than in each thing that draws. The board in the world does the same thing with a camera
     * that faces the other way, which is the same rotation by another means.
     */
    public TableCamera {
        spanX = Math.max(1, spanX);
        spanY = Math.max(1, spanY);
        centerX = Math.max(0, Math.min(spanX, centerX));
        centerY = Math.max(0, Math.min(spanY, centerY));
        scale = clampScale(scale);
    }

    /**
     * A camera over a single table, which is the shape most of them are.
     * <p>The surface used to be assumed square and one table across, and the assumption was
     * spread through the arithmetic rather than stated. It is stated now, and this is the
     * common case rather than the only one.
     */
    public TableCamera(double centerX, double centerY, double scale) {
        this(centerX, centerY, scale, TablePosition.SPAN, TablePosition.SPAN, false);
    }

    /** The same view, seen from the chair on the other side of the table. */
    public TableCamera seenFrom(boolean farSide) {
        return turned == farSide ? this : new TableCamera(centerX, centerY, scale, spanX, spanY, farSide);
    }

    /** Which way the surface runs against the screen: forwards, or turned around. */
    private double sense() {
        return turned ? -1 : 1;
    }

    /**
     * How much of the view a framed table leaves clear, at each edge.
     * <p>Small: enough to see that the border is there, not enough to make the board smaller
     * in any way somebody would notice.
     */
    private static final double EDGE = 0.02;

    /**
     * A camera showing the whole table in a viewport of this size.
     * <p>What a player gets the first time they sit down: everything, at whatever size
     * everything happens to be, rather than a close-up of a corner they have to find their way
     * out of.
     */
    public static TableCamera showingAll(int viewportWidth, int viewportHeight) {
        return showingAll(TablePosition.SPAN, TablePosition.SPAN, viewportWidth, viewportHeight);
    }

    /**
     * A camera showing the whole of a surface this size in a viewport that size.
     * <p>The surface's own proportions, not a square: two tables pushed together are twice as
     * wide as they are deep, and fitting that to a square left the board in a box in the
     * middle of the screen with the rest of the window empty.
     */
    public static TableCamera showingAll(
            int surfaceWidth, int surfaceHeight, int viewportWidth, int viewportHeight) {
        int across = Math.max(1, surfaceWidth);
        int down = Math.max(1, surfaceHeight);
        // Inside the viewport rather than exactly filling it. A table fitted edge to edge is
        // a table whose far border is drawn on the same row of pixels as whatever bounds the
        // view - so "show the whole table" produced a far mat with no visible top edge,
        // which reads as a crop rather than as the whole thing.
        double fit = Math.min(
                Math.max(1, viewportWidth) * (1 - EDGE * 2) / across,
                Math.max(1, viewportHeight) * (1 - EDGE * 2) / down);
        return new TableCamera(across / 2.0, down / 2.0, fit, across, down, false);
    }

    // ------------------------------------------------------------- the math

    public double toScreenX(double tableX, int viewportWidth) {
        return viewportWidth / 2.0 + (tableX - centerX) * scale * sense();
    }

    public double toScreenY(double tableY, int viewportHeight) {
        return viewportHeight / 2.0 + (tableY - centerY) * scale * sense();
    }

    public double toTableX(double screenX, int viewportWidth) {
        return centerX + (screenX - viewportWidth / 2.0) / scale * sense();
    }

    public double toTableY(double screenY, int viewportHeight) {
        return centerY + (screenY - viewportHeight / 2.0) / scale * sense();
    }

    /**
     * How tall a reference card is at this zoom, which is how far in or out the view is.
     * <p>A readout of {@link #scale} in the units the limits are written in, not the size of
     * anything on the board - see {@link #REFERENCE_CARD_HEIGHT}.
     */
    public int referenceCardPixels() {
        return Math.max(1, (int) Math.round(REFERENCE_CARD_HEIGHT * scale));
    }

    // ------------------------------------------------------------- moving it

    /** Drags the view by a number of screen pixels, which is what a pan gesture produces. */
    public TableCamera pannedBy(double pixelsX, double pixelsY) {
        return new TableCamera(
                centerX - pixelsX / scale * sense(), centerY - pixelsY / scale * sense(),
                scale, spanX, spanY, turned);
    }

    /**
     * Zooms about a point on screen, so whatever is under the cursor stays under it.
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
        // Solve for the center that keeps the anchor under the same pixel at the new scale.
        return new TableCamera(
                anchorX - (screenX - width / 2.0) / zoomed * sense(),
                anchorY - (screenY - height / 2.0) / zoomed * sense(),
                zoomed, spanX, spanY, turned);
    }

    public boolean isAtClosest() {
        return scale >= MAX_CARD_PIXELS / REFERENCE_CARD_HEIGHT;
    }

    public boolean isAtFurthest() {
        return scale <= MIN_CARD_PIXELS / REFERENCE_CARD_HEIGHT;
    }

    private static double clampScale(double wanted) {
        double lowest = MIN_CARD_PIXELS / REFERENCE_CARD_HEIGHT;
        double highest = MAX_CARD_PIXELS / REFERENCE_CARD_HEIGHT;
        return Math.max(lowest, Math.min(highest, wanted));
    }

}
