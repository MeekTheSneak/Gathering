package dev.gathering.core.ui;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.table.SeatAnchor;
import java.util.List;

/**
 * Everything the seated view needs to turn a card's position into a rectangle, and back.
 *
 * <p>Two steps, and both of them already exist: {@link TableSurface} says where a seat's mat is
 * on the shared table and where a position on that mat lands, and {@link TableCamera} says
 * which part of the table is on screen. This is the pair of them held together, so that
 * drawing a card and working out what the cursor is over cannot go through different
 * arithmetic.
 *
 * <p>Mutable, unlike everything under it. The camera changes several times a second while
 * somebody pans, and threading a new one back through the screen on every scroll wheel tick
 * would be ceremony around a field.
 *
 * <p>Pure: no Minecraft in here, which is what lets the round trip between a card's position
 * and its rectangle be tested directly rather than looked at.
 */
public final class BoardGeometry implements BoardPlacement {

    private TableSurface surface;
    private TableCamera camera;
    private int width;
    private int height;

    /**
     * How much of the bottom of the screen something else is sitting on.
     *
     * <p>The hand. The felt runs under it - a table that stopped where your cards begin would
     * have a strip you could see across and never put anything on - but framing the board has
     * to leave it out, or the first thing a player sees is a board with its near edge behind
     * their own hand.
     */
    private int coveredAtTheBottom;

    /**
     * How far the opening view leans off your own board towards the middle of the table.
     *
     * <p>Enough to keep the near edge of the board opposite in view, not enough to push your
     * own zones off the bottom. A quarter of the way is the most that holds both.
     */
    private static final double LEAN_TOWARDS_THE_TABLE = 0.25;

    public BoardGeometry(List<SeatAnchor> anchors, int width, int height) {
        this(anchors, width, height, 0);
    }

    public BoardGeometry(List<SeatAnchor> anchors, int width, int height, int coveredAtTheBottom) {
        this.surface = TableSurface.forSeats(anchors);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.coveredAtTheBottom = Math.max(0, coveredAtTheBottom);
        showEverything();
    }

    /**
     * Takes a new table shape without moving the view.
     *
     * <p>Somebody sitting down adds a mat and rearranges the rest. Resetting the camera then
     * would yank the table out from under whoever was mid-turn, so the view stays where it is.
     */
    public void reshape(List<SeatAnchor> anchors, int newWidth, int newHeight) {
        reshape(anchors, newWidth, newHeight, coveredAtTheBottom);
    }

    public void reshape(
            List<SeatAnchor> anchors, int newWidth, int newHeight, int newCoveredAtTheBottom) {
        this.surface = TableSurface.forSeats(anchors);
        this.width = Math.max(1, newWidth);
        this.height = Math.max(1, newHeight);
        this.coveredAtTheBottom = Math.max(0, newCoveredAtTheBottom);
    }

    @Override
    public TableSurface surface() {
        return surface;
    }

    public TableCamera camera() {
        return camera;
    }

    /**
     * The view a player starts at: their own board, as large as the window allows.
     *
     * <p>Not the whole table. Fitting everything in sounds like the friendly default and plays
     * terribly - a two-seat table squeezed into a short window draws a card twenty-seven pixels
     * wide, which is a coloured smudge, while the same card in the player's hand is ninety. You
     * cannot play on a board you cannot read, and the board you need to read is your own.
     *
     * <p>So: fitted across the width, which is the dimension a widescreen window has to spare,
     * and centred on this seat's own mat. The opponent's board is still there, just above or
     * below - and Home still steps back to show all of it.
     */
    public void focusOn(SeatId seat) {
        Rect mat = surface.matOf(seat.index());
        if (mat.isEmpty()) {
            showEverything();
            return;
        }
        double fit = width / (double) Math.max(1, surface.width());
        camera = new TableCamera(mat.centreX(), mat.centreY(), fit,
                surface.width(), surface.height());
        if (coveredAtTheBottom > 0) {
            camera = camera.pannedBy(0, -coveredAtTheBottom / 2.0);
        }

        // Leaned towards the middle of the table, so the board opposite comes into view as
        // soon as the window has room for it. Bounded by the room there actually is: a mat
        // three hundred pixels deep in a strip three hundred and fifty deep has fifty pixels
        // of slack, and leaning further than that pushes the near edge of your own board -
        // your zones, the part you use most - off the top of the screen to show somebody
        // else's. Both boards at once and a readable card are not both possible on a small
        // window, and of the two it is your own board that has to win.
        double slack = Math.max(0, visible() - mat.height() * camera.scale()) / 2.0;
        double wanted = (surface.height() / 2.0 - mat.centreY()) * LEAN_TOWARDS_THE_TABLE;
        double lean = Math.max(-slack, Math.min(slack, wanted * camera.scale())) / camera.scale();
        camera = new TableCamera(
                camera.centreX(), camera.centreY() + lean,
                camera.scale(), surface.width(), surface.height());
    }

    /** How much of the window is not the hand, which is what the board is framed into. */
    private int visible() {
        return Math.max(1, height - coveredAtTheBottom);
    }

    public void showEverything() {
        int visible = visible();
        camera = TableCamera.showingAll(surface.width(), surface.height(), width, visible);
        // Fitted to the part of the screen that is not the hand, then nudged up so it sits in
        // the middle of that part rather than the middle of the window.
        if (coveredAtTheBottom > 0) {
            camera = camera.pannedBy(0, -coveredAtTheBottom / 2.0);
        }
    }

    public void pan(double pixelsX, double pixelsY) {
        camera = camera.pannedBy(pixelsX, pixelsY);
    }

    public void zoom(double factor, double atX, double atY) {
        camera = camera.zoomedAt(factor, atX, atY, width, height);
    }

    // ---------------------------------------------------------- card to screen

    /** Where a card sitting at this position on this seat's mat is drawn. */
    @Override
    public Rect rectOf(SeatId seat, TablePosition position) {
        double surfaceX = surface.surfaceX(seat.index(), position.x());
        double surfaceY = surface.surfaceY(seat.index(), position.y());
        int cardWidth = cardWidth(seat);
        int cardHeight = cardHeight(seat);
        // Measured from the middle - see BoardPlacement.
        return new Rect(
                (int) Math.round(camera.toScreenX(surfaceX, width)) - cardWidth / 2,
                (int) Math.round(camera.toScreenY(surfaceY, height)) - cardHeight / 2,
                cardWidth,
                cardHeight);
    }

    /**
     * How big a card is drawn, which depends on whose mat it is on.
     *
     * <p>A mat is a fraction of the table and a card is a fraction of a mat, so a table with
     * eight seats draws smaller cards than one with two - exactly as a real one would, and for
     * the same reason.
     *
     * <p>The rule itself lives on {@link TableSurface}, because the table in the world draws
     * this same board and two answers to how big a card is would be two different boards.
     */
    @Override
    public int cardWidth(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardWidthOn(seat.index()) * camera.scale()));
    }

    @Override
    public int cardHeight(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardHeightOn(seat.index()) * camera.scale()));
    }

    // ---------------------------------------------------------- screen to card

    /** The position a card's corner would have if dropped here on this seat's mat. */
    @Override
    public TablePosition positionOn(SeatId seat, double screenX, double screenY) {
        return surface.positionOn(
                seat.index(),
                camera.toTableX(screenX, width),
                camera.toTableY(screenY, height));
    }

    /** Whose mat is under this screen point, or null for the felt between them. */
    @Override
    public SeatId seatAt(double screenX, double screenY) {
        int seat = surface.seatAt(
                camera.toTableX(screenX, width), camera.toTableY(screenY, height));
        return seat < 0 ? null : new SeatId(seat);
    }

    /** A seat's mat, as a rectangle on screen. */
    @Override
    public Rect matRect(SeatId seat) {
        return surfaceRect(surface.matOf(seat.index()));
    }

    /** One of a seat's piles, as a rectangle on screen. */
    @Override
    public Rect pileRect(SeatId seat, int index, int count) {
        return surfaceRect(surface.pileSlot(seat.index(), index, count));
    }

    @Override
    public int pileAt(SeatId seat, int count, double screenX, double screenY) {
        return surface.pileAt(seat.index(), count,
                camera.toTableX(screenX, width), camera.toTableY(screenY, height));
    }

    private Rect surfaceRect(Rect onSurface) {
        if (onSurface.isEmpty()) {
            return Rect.NONE;
        }
        int left = (int) Math.round(camera.toScreenX(onSurface.x(), width));
        int top = (int) Math.round(camera.toScreenY(onSurface.y(), height));
        int right = (int) Math.round(camera.toScreenX(onSurface.right(), width));
        int bottom = (int) Math.round(camera.toScreenY(onSurface.bottom(), height));
        return new Rect(left, top, right - left, bottom - top);
    }
}
