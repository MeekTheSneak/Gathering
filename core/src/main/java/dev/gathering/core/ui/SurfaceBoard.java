package dev.gathering.core.ui;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.table.SeatAnchor;
import java.util.List;

/**
 * The board measured on the table itself, for the view that plays on the block.
 *
 * <p>The whole of this is {@link TableSurface} wearing the shape the screen expects. There is
 * no camera in it and there is not meant to be: the game's own camera is already over the
 * table, so by the time a cursor has been turned into a point on the surface the looking has
 * been done and what is left is the same arithmetic the seated view does after its own camera
 * has had its turn.
 *
 * <p>Which is the point. Two views, one set of answers about where things are.
 */
public final class SurfaceBoard implements BoardPlacement {

    private TableSurface surface;

    public SurfaceBoard(List<SeatAnchor> anchors) {
        this.surface = TableSurface.forSeats(anchors);
    }

    /** Takes a new table shape. There is no view here to disturb, so it is only the mats. */
    public void reshape(List<SeatAnchor> anchors) {
        this.surface = TableSurface.forSeats(anchors);
    }

    @Override
    public TableSurface surface() {
        return surface;
    }

    @Override
    public Rect rectOf(SeatId seat, TablePosition position) {
        int width = cardWidth(seat);
        int height = cardHeight(seat);
        return new Rect(
                (int) Math.round(surface.surfaceX(seat.index(), position.x())) - width / 2,
                (int) Math.round(surface.surfaceY(seat.index(), position.y())) - height / 2,
                width,
                height);
    }

    @Override
    public int cardWidth(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardWidthOn(seat.index())));
    }

    @Override
    public int cardHeight(SeatId seat) {
        return Math.max(1, (int) Math.round(surface.cardHeightOn(seat.index())));
    }

    /** The surface's own answer: the camera looking at it does the viewer's half of the turn. */
    @Override
    public int facingDegrees(SeatId seat) {
        return surface.facingDegrees(seat.index());
    }

    @Override
    public TablePosition positionOn(SeatId seat, double x, double y) {
        return surface.positionOn(seat.index(), x, y);
    }

    @Override
    public SeatId seatAt(double x, double y) {
        int seat = surface.seatAt(x, y);
        return seat < 0 ? null : new SeatId(seat);
    }

    @Override
    public Rect matRect(SeatId seat) {
        return surface.matOf(seat.index());
    }

    @Override
    public Rect pileRect(SeatId seat, int index, int count) {
        return surface.pileSlot(seat.index(), index, count);
    }

    @Override
    public Rect pileGroupRect(SeatId seat, int fromIndex, int toIndex, int count) {
        return surface.pileGroup(seat.index(), fromIndex, toIndex, count);
    }

    @Override
    public Rect matDividerRect(SeatId seat, int count) {
        return surface.matDivider(seat.index(), count);
    }

    @Override
    public Rect pileLabelRect(SeatId seat, int index, int count) {
        return surface.pileLabel(seat.index(), index, count);
    }

    @Override
    public int pileAt(SeatId seat, int count, double x, double y) {
        return surface.pileAt(seat.index(), count, x, y);
    }
}
