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
public final class BoardGeometry {

    private TableSurface surface;
    private TableCamera camera;
    private int width;
    private int height;

    public BoardGeometry(List<SeatAnchor> anchors, int width, int height) {
        this.surface = TableSurface.forSeats(anchors);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.camera = TableCamera.showingAll(this.width, this.height);
    }

    /**
     * Takes a new table shape without moving the view.
     *
     * <p>Somebody sitting down adds a mat and rearranges the rest. Resetting the camera then
     * would yank the table out from under whoever was mid-turn, so the view stays where it is.
     */
    public void reshape(List<SeatAnchor> anchors, int newWidth, int newHeight) {
        this.surface = TableSurface.forSeats(anchors);
        this.width = Math.max(1, newWidth);
        this.height = Math.max(1, newHeight);
    }

    public TableSurface surface() {
        return surface;
    }

    public TableCamera camera() {
        return camera;
    }

    public void showEverything() {
        camera = TableCamera.showingAll(width, height);
    }

    public void pan(double pixelsX, double pixelsY) {
        camera = camera.pannedBy(pixelsX, pixelsY);
    }

    public void zoom(double factor, double atX, double atY) {
        camera = camera.zoomedAt(factor, atX, atY, width, height);
    }

    // ---------------------------------------------------------- card to screen

    /** Where a card sitting at this position on this seat's mat is drawn. */
    public Rect screenRect(SeatId seat, TablePosition position) {
        double surfaceX = surface.surfaceX(seat.index(), position.x());
        double surfaceY = surface.surfaceY(seat.index(), position.y());
        return new Rect(
                (int) Math.round(camera.toScreenX(surfaceX, width)),
                (int) Math.round(camera.toScreenY(surfaceY, height)),
                cardWidth(seat),
                cardHeight(seat));
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
    public int cardWidth(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardWidthOn(seat.index()) * camera.scale()));
    }

    public int cardHeight(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardHeightOn(seat.index()) * camera.scale()));
    }

    // ---------------------------------------------------------- screen to card

    /** The position a card's corner would have if dropped here on this seat's mat. */
    public TablePosition positionOn(SeatId seat, double screenX, double screenY) {
        return surface.positionOn(
                seat.index(),
                camera.toTableX(screenX, width),
                camera.toTableY(screenY, height));
    }

    /** Whose mat is under this screen point, or null for the felt between them. */
    public SeatId seatAt(double screenX, double screenY) {
        int seat = surface.seatAt(
                camera.toTableX(screenX, width), camera.toTableY(screenY, height));
        return seat < 0 ? null : new SeatId(seat);
    }

    /** A seat's mat, as a rectangle on screen. */
    public Rect matRect(SeatId seat) {
        return surfaceRect(surface.matOf(seat.index()));
    }

    /** One of a seat's piles, as a rectangle on screen. */
    public Rect pileRect(SeatId seat, int index, int count) {
        return surfaceRect(surface.pileSlot(seat.index(), index, count));
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
