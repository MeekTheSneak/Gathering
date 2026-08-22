package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole cluster as one surface, with a playmat for every seat.
 *
 * <p>Until now a card's position meant "somewhere on my own board" and a screen showed one
 * board at a time. That is not a table - it is four boards you flick between, and you cannot
 * see what your opponent is doing while you do something. This lays every seat's mat out on
 * one surface, so a single camera over it shows the whole game.
 *
 * <p>A mat is where somebody sits, worked out from the cluster's own shape: each table cell
 * takes its share of the surface, and each seat takes the part of its cell nearest the edge it
 * sits at. Push two tables together and the mats move accordingly, because the cluster arithmetic
 * already decided where the seats are and this only has to agree with it.
 *
 * <p>A card keeps saying where it is on <em>its own mat</em>, which is the one thing that must
 * not change: positions are state, they are in the log and in undo, and a card whose meaning
 * depends on how many tables are currently pushed together would move when somebody built a
 * table two blocks away.
 */
public record TableSurface(List<Rect> mats) {

    /** The surface is the same square a card's position is measured in, so the maths is one step. */
    public static final int SPAN = TablePosition.SPAN;

    /** A gap around each mat, so two mats read as two rather than as one big felt. */
    private static final int MAT_INSET = SPAN / 120;

    public TableSurface {
        mats = List.copyOf(mats);
    }

    /**
     * Lays out one mat per seat, in seat order.
     *
     * <p>Seat order is the session's own numbering, so mat <i>n</i> belongs to seat <i>n</i>
     * and nothing has to be looked up by cell.
     */
    public static TableSurface forSeats(List<SeatAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return new TableSurface(List.of());
        }
        Rect[] cells = cellRects(anchors);
        Map<TableCell, List<Integer>> byCell = new LinkedHashMap<>();
        for (int index = 0; index < anchors.size(); index++) {
            byCell.computeIfAbsent(anchors.get(index).cell(), ignored -> new ArrayList<>()).add(index);
        }

        Rect[] mats = new Rect[anchors.size()];
        byCell.forEach((cell, seatIndexes) -> {
            Side along = anchors.get(seatIndexes.get(0)).side();
            List<Rect> shares = split(cells[seatIndexes.get(0)], seatIndexes.size(), along);

            // Ranked, then handed out in order. Mapping each side straight to an end collides
            // the moment a cell seats three - south and east both want the last band, and two
            // players end up sharing one mat while a band goes spare.
            List<Integer> ranked = new ArrayList<>(seatIndexes);
            ranked.sort(java.util.Comparator.comparingInt(
                    seat -> rankAlong(anchors.get(seat).side(), along)));
            for (int band = 0; band < ranked.size(); band++) {
                mats[ranked.get(band)] = shares.get(band);
            }
        });
        List<Rect> inset = new ArrayList<>(mats.length);
        for (Rect mat : mats) {
            inset.add(mat.shrink(MAT_INSET));
        }
        return new TableSurface(inset);
    }

    /**
     * How far along the split a side wants to be.
     *
     * <p>Bands run top to bottom when a cell is cut across, and left to right when it is cut
     * down, so the side facing the start of that run ranks first and its opposite ranks last.
     * The two sides at right angles to the run land in the middle - they have no preference
     * along it, and what matters is only that they get a band of their own.
     */
    private static int rankAlong(Side side, Side along) {
        boolean horizontal = along == Side.NORTH || along == Side.SOUTH;
        Side first = horizontal ? Side.NORTH : Side.WEST;
        if (side == first) {
            return 0;
        }
        if (side == first.opposite()) {
            return 2;
        }
        return 1;
    }

    /**
     * Cuts an area into equal bands.
     *
     * <p>Across the short way for a north or south seat and down the long way for east or
     * west, so a mat is always wider than it is deep - which is the shape of the space in front
     * of somebody sitting at a table, and the shape a row of lands wants.
     */
    private static List<Rect> split(Rect area, int count, Side along) {
        List<Rect> shares = new ArrayList<>(count);
        boolean horizontal = along == Side.NORTH || along == Side.SOUTH;
        for (int index = 0; index < count; index++) {
            if (horizontal) {
                int height = area.height() / count;
                shares.add(new Rect(area.x(), area.y() + index * height, area.width(), height));
            } else {
                int width = area.width() / count;
                shares.add(new Rect(area.x() + index * width, area.y(), width, area.height()));
            }
        }
        return shares;
    }

    /** Each seat's table cell, mapped into the surface by where that cell sits in the cluster. */
    private static Rect[] cellRects(List<SeatAnchor> anchors) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (SeatAnchor anchor : anchors) {
            minX = Math.min(minX, anchor.cell().x());
            minZ = Math.min(minZ, anchor.cell().z());
            maxX = Math.max(maxX, anchor.cell().x());
            maxZ = Math.max(maxZ, anchor.cell().z());
        }
        int columns = maxX - minX + 1;
        int rows = maxZ - minZ + 1;

        Rect[] rects = new Rect[anchors.size()];
        for (int index = 0; index < anchors.size(); index++) {
            TableCell cell = anchors.get(index).cell();
            int width = SPAN / columns;
            int height = SPAN / rows;
            rects[index] = new Rect(
                    (cell.x() - minX) * width, (cell.z() - minZ) * height, width, height);
        }
        return rects;
    }

    // ------------------------------------------------------------ the maths

    public Rect matOf(int seat) {
        return seat >= 0 && seat < mats.size() ? mats.get(seat) : Rect.NONE;
    }

    public int seatCount() {
        return mats.size();
    }

    /**
     * Where a card sitting at this position on this seat's mat is, on the whole surface.
     *
     * <p>The one conversion between "where on my board" - which is what the game stores - and
     * "where on the table", which is what a camera looking at everything needs.
     */
    public double surfaceX(int seat, double positionX) {
        Rect mat = matOf(seat);
        return mat.x() + positionX * mat.width() / (double) SPAN;
    }

    public double surfaceY(int seat, double positionY) {
        Rect mat = matOf(seat);
        return mat.y() + positionY * mat.height() / (double) SPAN;
    }

    /** The reverse: a point on the surface, as a position on this seat's mat. */
    public TablePosition positionOn(int seat, double surfaceX, double surfaceY) {
        Rect mat = matOf(seat);
        if (mat.isEmpty()) {
            return TablePosition.ORIGIN;
        }
        return TablePosition.clamped(
                (int) Math.round((surfaceX - mat.x()) * SPAN / (double) mat.width()),
                (int) Math.round((surfaceY - mat.y()) * SPAN / (double) mat.height()));
    }

    /** Whose mat a surface point is on, or -1 for the felt between them. */
    public int seatAt(double surfaceX, double surfaceY) {
        for (int seat = 0; seat < mats.size(); seat++) {
            Rect mat = mats.get(seat);
            if (!mat.isEmpty()
                    && surfaceX >= mat.x() && surfaceX < mat.right()
                    && surfaceY >= mat.y() && surfaceY < mat.bottom()) {
                return seat;
            }
        }
        return -1;
    }
}
