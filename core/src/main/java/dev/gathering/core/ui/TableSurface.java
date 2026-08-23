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

    /**
     * A gap around each mat, so two mats read as two rather than as one big felt.
     *
     * <p>Two pixels. The surface is the table's whole footprint, so a pixel is a sixteenth of
     * a block and the whole span is two blocks across: two pixels is a two-thirty-second of
     * the span, and a playmat comes out a fraction under the two-by-one it sits in.
     */
    private static final int MAT_INSET = SPAN * 2 / 32;

    /**
     * The most of a mat the border is ever allowed to eat, per side.
     *
     * <p>Two pixels is right on a table somebody is actually playing at, and wrong on a mat
     * that has been squeezed - a cell seating three cuts one into thirds, and two pixels off
     * every side of that took a mat down to a sliver a card would not fit on. So the border
     * is two pixels or an eighth of the mat, whichever is less.
     */
    private static final int MAT_INSET_SHARE = 8;

    /**
     * How many cards fit across a mat.
     *
     * <p>This is what decides how big a card is, and it is a number about playing rather than
     * about arithmetic: a real playmat is about nine cards wide, which is a row of lands with
     * room to spare. Deriving the size from the mat's shorter side instead - which is what it
     * used to do - gave a two-player table cards a twentieth of its width, and a board that
     * read as a mosaic from directly above it.
     */
    private static final int CARDS_ACROSS_A_MAT = 8;

    /** The gap between two piles in the row, as a fraction of a card. */
    private static final double PILE_GAP = 0.12;

    /**
     * How wide a card is on a table with one mat on it, in surface units.
     *
     * <p>Ten cards across. That is roughly what a real table holds in a row, and it is what
     * makes a zoomed-out board readable rather than a mosaic.
     */
    public static final double CARD_WIDTH_UNITS = SPAN / 10.0;

    private static final double CARD_ASPECT = 488.0 / 680.0;

    /** How tall a card is on that same table, in surface units. */
    public static final double CARD_HEIGHT_UNITS = CARD_WIDTH_UNITS / CARD_ASPECT;

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
            inset.add(mat.shrink(borderFor(mat)));
        }
        return new TableSurface(inset);
    }

    private static int borderFor(Rect mat) {
        return Math.min(MAT_INSET, Math.min(mat.width(), mat.height()) / MAT_INSET_SHARE);
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

    /**
     * Where a seat's piles sit: a row of card-shaped slots along the near edge of their mat.
     *
     * <p>On the table rather than in a side column, because a library is an object you reach
     * for. Along the edge nearest the player for the same reason a real one sits there - it is
     * the part of the mat you never put permanents on - and at the right-hand end of it,
     * which is where a right-handed player's deck goes and where every digital client since
     * has put it.
     *
     * <p><b>Card-shaped, exactly.</b> These used to be a share of the mat's width by a share
     * of its depth, which on a two-player table made each one wider than it was tall and drew
     * a library as a letterbox. A pile is a stack of cards and has to be the shape of one, or
     * the card sitting on top of it is stretched to fit a slot that is not card-shaped.
     *
     * @param index which pile, left to right
     * @param count how many piles the row holds
     */
    public Rect pileSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        int width = Math.max(1, (int) Math.round(cardWidthOn(seat)));
        int height = Math.max(1, (int) Math.round(cardHeightOn(seat)));
        int step = width + (int) Math.round(width * PILE_GAP);

        // Right-aligned along the near edge, and pushed in by the same gap so the last pile is
        // not flush against the mat's border.
        int gap = step - width;
        int right = mat.right() - gap;
        int left = right - count * step + gap;
        return new Rect(left + index * step, mat.bottom() - gap - height, width, height);
    }

    /**
     * Which of this seat's piles a surface point is on, or -1.
     *
     * <p>Here rather than in whatever is drawing, because dropping a card into a zone and
     * drawing that zone have to agree about where it is - and a drop that lands a pixel off
     * the graveyard it looks like it is over is the kind of thing nobody reports as a bug,
     * they just stop using it.
     */
    public int pileAt(int seat, int count, double surfaceX, double surfaceY) {
        for (int index = 0; index < count; index++) {
            if (pileSlot(seat, index, count).contains((int) Math.round(surfaceX), (int) Math.round(surfaceY))) {
                return index;
            }
        }
        return -1;
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

    /**
     * How big a card is on this seat's mat, against a card on a table with one mat on it.
     *
     * <p>A mat is a share of the table and a card is a share of a mat, so a table with eight
     * seats draws smaller cards than one with two - exactly as a real one would, and for the
     * same reason.
     *
     * <p>One factor for both axes, taken from whichever side of the mat is tighter. Scaling
     * width by the mat's width and height by its height looks like the obvious thing and is
     * wrong: mats are rarely square - two players at one table get a mat that is the full
     * width and half the depth - so a card drawn that way comes out squat and half again as
     * wide as it is tall. A card is a card whatever shape the board under it is.
     *
     * <p>Here rather than in whatever happens to be drawing, because the screen and the table
     * in the world both draw this board, and two answers to how big a card is would be two
     * different boards.
     */
    public double cardScale(int seat) {
        return cardWidthOn(seat) / CARD_WIDTH_UNITS;
    }

    /** A card's width on this seat's mat, in surface units. */
    public double cardWidthOn(int seat) {
        Rect mat = matOf(seat);
        return mat.isEmpty() ? 0 : mat.width() / (double) CARDS_ACROSS_A_MAT;
    }

    /** A card's height on this seat's mat, in surface units. */
    public double cardHeightOn(int seat) {
        return cardWidthOn(seat) * CARD_HEIGHT_UNITS / CARD_WIDTH_UNITS;
    }
}
