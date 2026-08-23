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
public record TableSurface(List<Rect> mats, List<Boolean> turned, int width, int height) {

    /**
     * How many units one table is across, both ways.
     *
     * <p>The same number a card's position is measured in, so the maths is one step - but the
     * <em>surface</em> is no longer this square. Two tables pushed together are four blocks by
     * two, and squashing that into a square stretched every mat on it into something no card
     * was ever the right shape for. The surface is however many tables across by however many
     * deep, in these units, and a mat comes out two by one wherever it sits.
     */
    public static final int SPAN = TablePosition.SPAN;

    /**
     * A gap around each mat, so two mats read as two rather than as one big felt.
     *
     * <p>Two pixels. The surface is the table's whole footprint, so a pixel is a sixteenth of
     * a block and the whole span is two blocks across: two pixels is a two-thirty-second of
     * the span, and a playmat comes out a fraction under the two-by-one it sits in.
     */
    private static final int MAT_INSET_PIXELS = 2;

    /** How many pixels a table is across, at sixteen to the block. */
    private static final int PIXELS_ACROSS_A_TABLE = TableCell.BLOCKS_PER_TABLE * 16;

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
     * about arithmetic. Deriving the size from the mat's shorter side instead - which is what
     * it used to do - gave a two-player table cards a twentieth of its width, and a board that
     * read as a mosaic from directly above it.
     *
     * <p>A real playmat is nearer nine across, but a real playmat is not this shape: two
     * blocks by one is wider and shallower than the twenty-four by fourteen inches a mat comes
     * in, and matching the width would leave only two and a half card-heights of depth. Eleven
     * across gives three rows - lands, creatures, and the zones along the near edge - which is
     * what a board actually needs.
     */
    private static final int CARDS_ACROSS_A_MAT = 11;

    /** The gap between two zones in the column, as a fraction of a slot. */
    private static final double PILE_GAP = 0.12;

    /** How many zones the column holds, which is what decides how big each slot can be. */
    private static final int PILE_COLUMN = 4;

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
        turned = List.copyOf(turned);
    }

    /** An empty table, which is what a cluster nobody is sitting at comes to. */
    public static TableSurface empty() {
        return new TableSurface(List.of(), List.of(), SPAN, SPAN);
    }

    /**
     * Whether this seat's board is laid out the other way up.
     *
     * <p>Two people at a table sit opposite each other, so their boards face each other: the
     * edge of the mat nearest its own player is the north edge for one of them and the south
     * edge for the other. Without this every board was laid out as though its player were
     * sitting at the bottom of the table, which put one player's zones along the far side of
     * their own mat and read, from their chair, as somebody else's board.
     */
    public boolean isTurned(int seat) {
        return seat >= 0 && seat < turned.size() && turned.get(seat);
    }

    /** A half turn for the seats that are laid out the other way up, so cards face their owner. */
    public int facingDegrees(int seat) {
        return isTurned(seat) ? 180 : 0;
    }

    /**
     * Lays out one mat per seat, in seat order.
     *
     * <p>Seat order is the session's own numbering, so mat <i>n</i> belongs to seat <i>n</i>
     * and nothing has to be looked up by cell.
     */
    public static TableSurface forSeats(List<SeatAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return empty();
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
        List<Boolean> facing = new ArrayList<>(mats.length);
        for (int index = 0; index < mats.length; index++) {
            inset.add(mats[index].shrink(borderFor(cells[index], mats[index])));
            facing.add(anchors.get(index).side() == Side.NORTH);
        }
        Rect bounds = boundsOf(anchors);
        return new TableSurface(inset, facing, bounds.width(), bounds.height());
    }

    /**
     * How much border a mat gets, in surface units.
     *
     * <p>Two pixels of the <em>table</em> rather than of the whole surface. Those are the same
     * thing on one table and nowhere near it on a cluster: with four seats the surface covers
     * two tables, so a border measured against the surface came out twice as thick and left
     * the four mats floating in a sea of felt with the gaps wider than the zones.
     */
    private static int borderFor(Rect cell, Rect mat) {
        int pixel = Math.min(cell.width(), cell.height()) / PIXELS_ACROSS_A_TABLE;
        return Math.max(1, Math.min(
                pixel * MAT_INSET_PIXELS,
                Math.min(mat.width(), mat.height()) / MAT_INSET_SHARE));
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
        Rect[] rects = new Rect[anchors.size()];
        for (int index = 0; index < anchors.size(); index++) {
            TableCell cell = anchors.get(index).cell();
            // One table is SPAN by SPAN, always. The surface grows with the cluster rather
            // than the tables shrinking to fit a square, which is what kept every mat the
            // shape of a mat.
            rects[index] = new Rect(
                    (cell.x() - minX) * SPAN, (cell.z() - minZ) * SPAN, SPAN, SPAN);
        }
        return rects;
    }

    /** How big the whole surface is, in table-sized units. */
    private static Rect boundsOf(List<SeatAnchor> anchors) {
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
        return new Rect(0, 0, (maxX - minX + 1) * SPAN, (maxZ - minZ + 1) * SPAN);
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
        double along = isTurned(seat) ? SPAN - positionX : positionX;
        return mat.x() + along * mat.width() / (double) SPAN;
    }

    public double surfaceY(int seat, double positionY) {
        Rect mat = matOf(seat);
        double down = isTurned(seat) ? SPAN - positionY : positionY;
        return mat.y() + down * mat.height() / (double) SPAN;
    }

    /** The reverse: a point on the surface, as a position on this seat's mat. */
    public TablePosition positionOn(int seat, double surfaceX, double surfaceY) {
        Rect mat = matOf(seat);
        if (mat.isEmpty()) {
            return TablePosition.ORIGIN;
        }
        int across = (int) Math.round((surfaceX - mat.x()) * SPAN / (double) mat.width());
        int down = (int) Math.round((surfaceY - mat.y()) * SPAN / (double) mat.height());
        return isTurned(seat)
                ? TablePosition.clamped(SPAN - across, SPAN - down)
                : TablePosition.clamped(across, down);
    }

    /**
     * Where a seat's zones sit: a column of card-shaped slots down the outer edge of their mat.
     *
     * <p>Down the side rather than across the near edge, which is where the tables people
     * already play on put them and, once you see it, obviously right: the near edge is the
     * part of a mat you reach across constantly, and a row of zones along it is four things to
     * knock into every time you play a land. The outer edge is dead space on every board.
     *
     * <p>Outer meaning away from the middle of the table, so on a four-seat surface the two
     * left-hand boards keep their zones on the left and the two right-hand ones on the right,
     * and the middle of the table stays clear.
     *
     * <p><b>Card-shaped, exactly.</b> These used to be a share of the mat's width by a share
     * of its depth, which on a two-player table made each one wider than it was tall and drew
     * a library as a letterbox. A zone holds a stack of cards and has to be the shape of one,
     * or the card on top of it is stretched to fit a slot that is not card-shaped.
     *
     * @param index which zone, top to bottom
     * @param count how many zones the column holds
     */
    public Rect pileSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        // A zone is a card, unless a column of them would be taller than the mat - which it is
        // on a two-player board, where a mat is twice as wide as it is deep. Then they shrink
        // together, which keeps them a set rather than letting the last one fall off the edge.
        int height = (int) Math.round(cardHeightOn(seat));
        int roomEach = (int) (mat.height() / (count * (1 + PILE_GAP) + PILE_GAP));
        height = Math.max(1, Math.min(height, roomEach));
        int width = Math.max(1, (int) Math.round(height * CARD_WIDTH_UNITS / CARD_HEIGHT_UNITS));

        int gap = Math.max(1, (int) Math.round(height * PILE_GAP));
        int step = height + gap;
        int top = mat.y() + (mat.height() - (count * step - gap)) / 2;
        // Zone nought sits nearest its own player, and the column runs away from them. Which
        // end of the mat that is depends on which chair the board belongs to.
        int slot = isTurned(seat) ? index : count - 1 - index;
        int left = onTheLeft(seat, mat) ? mat.x() + gap : mat.right() - gap - width;
        return new Rect(left, top + slot * step, width, height);
    }

    /**
     * Whether this mat's outer edge is its left-hand one.
     *
     * <p>Stated against the table and not against the chair, deliberately. Turning it with the
     * board sounds more careful and puts both zone columns in the middle of the table, facing
     * each other across the gap - which is the one part of the surface everybody reaches over.
     * Away from the middle is what leaves the middle clear, and it happens to put every
     * player's zones on their own right hand, which is where a deck goes anyway.
     */
    private boolean onTheLeft(int seat, Rect mat) {
        return mat.centreX() < width / 2.0;
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
