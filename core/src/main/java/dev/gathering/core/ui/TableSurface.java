package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole cluster as one surface, with a playmat for every seat, so one camera shows the
 * whole game rather than one board at a time.
 * <p>A mat is a seat's share of its table cell, taken from the edge that seat sits at, so the
 * mats follow whatever shape the cluster arithmetic already decided on.
 * <p>A card's position stays relative to <em>its own mat</em>. Positions are state - they are
 * in the log and in undo - so a position that meant something different once another table was
 * pushed against this one would move cards when somebody built two blocks away.
 */
public record TableSurface(List<Rect> mats, List<Boolean> turned, int width, int height) {

    /**
     * How many units one table is across, both ways - the same units a card's position is in.
     * <p>The surface itself is not square: it is however many tables across by however many
     * deep, so a mat comes out two by one wherever it sits.
     */
    public static final int SPAN = TablePosition.SPAN;

    /**
     * A gap around each mat, so two mats read as two rather than as one big felt.
     * <p>Never less than two, for the life totals rather than the felt: each seat's sits just
     * past the far edge of its mat, so the strip between two facing mats holds two of them back
     * to back and they overlap at one pixel. A test covers it.
     */
    private static final int MAT_INSET_PIXELS = 2;

    /** How many pixels a table is across, at sixteen to the block. */
    private static final int PIXELS_ACROSS_A_TABLE = TableCell.BLOCKS_PER_TABLE * 16;

    /**
     * The most of a mat the border may eat, per side: two pixels or an eighth, whichever is
     * less. A cell seating three cuts each mat into thirds, and a flat two pixels off every
     * side of that leaves a sliver no card fits on.
     */
    private static final int MAT_INSET_SHARE = 8;

    /**
     * How many cards fit across a mat, which is what decides how big a card is.
     * <p>Chosen for play rather than derived: a real playmat is nearer nine across, but this
     * mat is two blocks by one and so wider and shallower than a real one. Eleven leaves three
     * rows deep - lands, creatures, and the zones along the near edge.
     */
    private static final int CARDS_ACROSS_A_MAT = 11;

    /** The gap between two zones in the column, as a fraction of a slot. */
    private static final double PILE_GAP = 0.12;

    /** And the wider one that sets the command zone apart from the other three. */
    private static final double PILE_GROUP_GAP = 0.45;

    /**
     * How far the box round a group sits outside the zones in it, as a share of the gap. Not
     * the whole gap: the column is already one gap in from the mat's edge, so a full-gap box
     * would draw its line exactly on the border.
     */
    private static final double PILE_GROUP_PAD = 0.8;

    /** How much of a command slot the tax band across its foot takes up. */
    private static final double TAX_BAND = 0.26;

    /** How deep the row nearest a player is, in card heights: one card and a little air. */
    private static final double LANDS_ROW = 1.15;

    /** And how heavy the line marking it is, as a share of a card's height. */
    private static final double DIVIDER_THICKNESS = 0.012;

    /** The gap between verb buttons, as a share of one button. */
    private static final double VERB_GAP = 0.16;

    /** How tall a verb button is, as a share of its width. */
    private static final double VERB_HEIGHT = 0.45;

    /**
     * How far in from its own edge of the mat anything printed down a side sits, in card
     * heights. One number for both sides: worked out separately they are shares of two
     * different gaps, and the mat comes out with an even margin down one side and a zone
     * column jammed against the border on the other.
     */
    private static final double EDGE_MARGIN = 0.23;

    /** How tall a seat's life counter is, as a share of a card. */
    private static final double LIFE_HEIGHT = 0.42;

    /** And how wide, as a share of its own height - room for three figures and two halves. */
    private static final double LIFE_WIDTH = 2.4;

    /** How far the counter stands off the edge of its own mat, as a share of its height. */
    private static final double LIFE_STANDOFF = 0.22;

    /** How much of a life counter each of its two ends takes up. */
    private static final double LIFE_END_ROOM = 0.28;

    /** How many slot widths of felt a zone's name is given to be written across. */
    private static final double PILE_LABEL_WIDTHS = 2.4;

    /** How tall that writing is, as a share of the slot it names. */
    private static final double PILE_LABEL_HEIGHT = 0.17;

    /**
     * How wide a card is on a table with one mat on it, in surface units.
     * <p>Ten cards across. That is roughly what a real table holds in a row, and it is what
     * makes a zoomed-out board readable rather than a mosaic.
     */
    public static final double CARD_WIDTH_UNITS = SPAN / 10.0;


    /** How tall a card is on that same table, in surface units. */
    public static final double CARD_HEIGHT_UNITS = CardShape.heightFor(CARD_WIDTH_UNITS);

    public TableSurface {
        mats = List.copyOf(mats);
        turned = List.copyOf(turned);
    }

    /** An empty table, which is what a cluster nobody is sitting at comes to. */
    public static TableSurface empty() {
        return new TableSurface(List.of(), List.of(), SPAN, SPAN);
    }

    /**
     * The layout for a table with this many seats, which is the shape everything asks for.
     * <p>Remembered rather than rebuilt. The table in the world lays this out on every frame
     * it is on screen, and a seat count is all it depends on - so a surface that was worked
     * out once was being worked out sixty times a second per table, allocating a map, two
     * arrays and a rectangle per seat each time, to arrive at the identical answer.
     * <p>Safe to share: a {@code TableSurface} is a record of immutable lists, and the answer
     * for a given seat count never changes. The map is concurrent because the client thread
     * and the render thread both ask.
     */
    public static TableSurface forSeatCount(int seats) {
        return BY_SEAT_COUNT.computeIfAbsent(
                Math.max(0, seats), count -> forSeats(TableCluster.assumedSeating(count)));
    }

    private static final java.util.concurrent.ConcurrentHashMap<Integer, TableSurface>
            BY_SEAT_COUNT = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether this seat's board is laid out the other way up.
     * <p>Players sit opposite each other, so the mat edge nearest its own player is north for
     * one of them and south for the other. Without it a seat's zones run along the far side of
     * its own mat, which from that chair reads as somebody else's board.
     */
    public boolean isTurned(int seat) {
        return seat >= 0 && seat < turned.size() && turned.get(seat);
    }

    /** A half turn for the seats that are laid out the other way up, so cards face their owner. */
    public int facingDegrees(int seat) {
        return isTurned(seat) ? 180 : 0;
    }

    /** One mat per seat, in the session's own seat order: mat <i>n</i> belongs to seat <i>n</i>. */
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
            // once a cell seats three: south and east both want the last band, so two players
            // share one mat and a band goes spare.
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
     * <p>Two pixels of the <em>table</em>, not of the surface. They differ on a cluster: a
     * border measured against a two-table surface comes out twice as thick, leaving the mats
     * floating with the gaps wider than the zones.
     */
    private static int borderFor(Rect cell, Rect mat) {
        int pixel = Math.min(cell.width(), cell.height()) / PIXELS_ACROSS_A_TABLE;
        return Math.max(1, Math.min(
                pixel * MAT_INSET_PIXELS,
                Math.min(mat.width(), mat.height()) / MAT_INSET_SHARE));
    }

    /**
     * How far along the split a side wants to be. Bands run top to bottom when a cell is cut
     * across and left to right when it is cut down, so the side facing the start of the run
     * ranks first and its opposite last. The two at right angles have no preference and only
     * need a band of their own.
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
     * Cuts an area into equal bands: across for a north or south seat, down for east or west,
     * so a mat is always wider than it is deep - the shape of the space in front of a chair,
     * and the shape a row of lands wants.
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
            // One table is SPAN by SPAN, always: the surface grows with the cluster rather
            // than the tables shrinking to fit a square.
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

    // ------------------------------------------------------------ the math

    public Rect matOf(int seat) {
        return seat >= 0 && seat < mats.size() ? mats.get(seat) : Rect.NONE;
    }

    public int seatCount() {
        return mats.size();
    }

    /**
     * Where a card at this position on this seat's mat is, on the whole surface: the one
     * conversion between "where on my board", which is what the game stores, and "where on the
     * table", which is what a camera over everything needs.
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
     * <p>Down the side rather than across the near edge, which is dead space on every board -
     * the near edge is reached across constantly, and zones along it are things to knock into.
     * Outer means the player's own right hand, so two players facing each other have their
     * columns on opposite edges of the surface; stated against the table instead, both land on
     * the same side and one player's is on their left.
     * <p>Card-shaped exactly, because a slot holds a stack of cards and the top one is drawn
     * to fit it. A share of the mat's width by a share of its depth draws a library as a
     * letterbox on a two-player table.
     * <p>The command zone is the last of the four and stands apart from the other three, which
     * is how a real board reads: three zones a hand is in and out of all game, and one it
     * touches twice. A format without commanders passes three and gets no gap.
     *
     * @param index which zone, nearest its own player first
     * @param count how many zones the column holds - four with a command zone, three without
     */
    public Rect pileSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        // A zone is a card, unless the column would be taller than the mat - which it is on a
        // two-player board. Then they shrink together and stay a set.
        boolean separated = count > Zone.PILES_WITHOUT_A_COMMAND_ZONE;
        double worth = count * (1 + PILE_GAP) + PILE_GAP + (separated ? PILE_GROUP_GAP : 0);
        // Fitted inside the mat's margin, not the whole mat: against the full height the
        // column runs edge to edge, putting the graveyard on the mat's own border.
        int height = Math.min(
                (int) Math.round(cardHeightOn(seat)), (int) (usableHeight(mat, seat) / worth));
        height = Math.max(1, height);
        int width = Math.max(1, CardShape.widthFor(height));

        int gap = Math.max(1, (int) Math.round(height * PILE_GAP));
        int apart = separated ? Math.max(1, (int) Math.round(height * PILE_GROUP_GAP)) : 0;
        int step = height + gap;
        int total = count * step - gap + apart;
        int top = mat.y() + (mat.height() - total) / 2;
        // Zone nought is nearest its own player and the column runs away from them, so which
        // end of the mat that is depends on the chair.
        int slot = isTurned(seat) ? index : count - 1 - index;
        // The mat's own margin, shared with the buttons down the other side. Taken from the
        // gap between zones it shrinks when they do, leaving a squeezed column against the
        // border while the buttons opposite keep their room.
        int inset = edgeMargin(seat);
        int left = isTurned(seat) ? mat.x() + inset : mat.right() - inset - width;
        return new Rect(left, top + slot * step + (slot >= breakAt(seat, count) ? apart : 0),
                width, height);
    }

    /**
     * Which slot down the surface the gap sits in front of. The command slots are at the far
     * end of the column from their player, so the gap comes before them for one chair and
     * after them for the one opposite.
     */
    private int breakAt(int seat, int count) {
        // However many the column has beyond the three a hand reaches for. From the count
        // rather than fixed at one, so a table with two sets both apart.
        int commandSlots = Math.max(0, count - Zone.PILES_WITHOUT_A_COMMAND_ZONE);
        return isTurned(seat) ? count - commandSlots : commandSlots;
    }

    /**
     * One of the buttons printed on a mat for the verbs a player uses every turn.
     * <p>On the player's own left, mirroring the zone column on their right: a rectangle of
     * felt with no affordances on it gives a new player no reason to think they can click it.
     * <p>A card wide and well under half that tall. Square, four of them come to three and a
     * half card heights - most of a mat's depth given over to four words. The width is what
     * the writing needs and so is what stays: a name is fitted across a button and shrinks
     * with it, and a narrowed button stops being written on at all.
     * <p>Index nought sits nearest its own player, the same way the zone column runs.
     */
    public Rect verbSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        int width = Math.max(1, (int) Math.round(cardWidthOn(seat)));
        // Shorter still where the run does not fit, the same way the zone column shrinks.
        double worth = count * (1 + VERB_GAP) + VERB_GAP;
        int height = Math.max(1, Math.min(
                (int) Math.round(width * VERB_HEIGHT), (int) (usableHeight(mat, seat) / worth)));
        int gap = Math.max(1, (int) Math.round(height * VERB_GAP));
        int step = height + gap;
        int total = count * step - gap;
        int top = mat.y() + (mat.height() - total) / 2;
        int slot = isTurned(seat) ? index : count - 1 - index;
        int inset = edgeMargin(seat);
        int left = isTurned(seat) ? mat.right() - inset - width : mat.x() + inset;
        return new Rect(left, top + slot * step, width, height);
    }

    /**
     * Where a seat's life total is written, on the table just past the far edge of its mat.
     * <p>Not on the mat: the board is where cards go, and a number in the play area is one
     * somebody puts a land on. Past the far edge it sits in the strip between the mats, which
     * is where counters go on a real table. Which edge is "far" depends on the chair, so both
     * players find their own number between their board and the middle.
     * <p>Empty where there is no room off the end of the mat - a number half under a mat is
     * worse than one in the status row alone.
     */
    public Rect lifeBox(int seat) {
        Rect mat = matOf(seat);
        if (mat.isEmpty()) {
            return Rect.NONE;
        }
        int height = Math.max(1, (int) Math.round(cardHeightOn(seat) * LIFE_HEIGHT));
        int width = Math.max(1, (int) Math.round(height * LIFE_WIDTH));
        int standoff = Math.max(1, (int) Math.round(height * LIFE_STANDOFF));
        // The far edge from this seat's own player, which is the one facing the middle of the
        // table. A turned mat is drawn upside down, so its far edge is the near one on screen.
        int top = isTurned(seat) ? mat.bottom() + standoff : mat.y() - standoff - height;
        int left = mat.x() + (mat.width() - width) / 2;
        Rect box = new Rect(left, top, width, height);
        // Room on the table for the whole of it, and clear of every other mat.
        if (box.y() < 0 || box.bottom() > height() || box.x() < 0 || box.right() > width()) {
            return Rect.NONE;
        }
        for (int other = 0; other < mats.size(); other++) {
            if (other != seat && box.overlaps(matOf(other))) {
                return Rect.NONE;
            }
        }
        return box;
    }

    /**
     * A seat's mat together with the life total printed off its far edge.
     * <p>What "your own board" means to a camera. The counter is off the mat but still part
     * of the board, and a view framing the mat alone puts the number the game is played to
     * outside the window. One rule, asked by both views.
     */
    public Rect ownBoard(int seat) {
        Rect mat = matOf(seat);
        Rect life = lifeBox(seat);
        if (mat.isEmpty() || life.isEmpty()) {
            return mat;
        }
        int left = Math.min(mat.x(), life.x());
        int top = Math.min(mat.y(), life.y());
        int right = Math.max(mat.right(), life.right());
        int bottom = Math.max(mat.bottom(), life.bottom());
        return new Rect(left, top, right - left, bottom - top);
    }

    /**
     * Which half of a seat's life counter a point is on: -1 for the left, 1 for the right, 0
     * for neither.
     * <p>Here rather than in whichever view took the click, because the halves are drawn as
     * well as pressed: a board drawing the plus on the side that takes one off is worse than
     * no button at all.
     * <p>Given the box rather than the seat: the two views measure one in different spaces,
     * pixels on the window and units of felt on the block. Given the facing for the same
     * reason - the seated camera has already turned the felt, so a box arrives in its own
     * player's frame; the board in the world turns each seat's writing instead.
     */
    public static int lifeWayAt(Rect box, boolean turned, double x, double y) {
        if (box.isEmpty() || !box.contains((int) Math.round(x), (int) Math.round(y))) {
            return 0;
        }
        boolean atTheLeft = x < box.centerX();
        return atTheLeft == turned ? 1 : -1;
    }

    /**
     * Whether a seat's counter is drawn turned about, in a view that may already have turned
     * the felt.
     * <p>The seated camera turns the whole surface, so a counter arrives already in its
     * player's frame. The board in the world turns nothing and writes each seat's marks facing
     * that seat. One method because the drawing and the press both ask: asking separately is
     * how the end marked plus came to take a life off on every seat facing the other way.
     */
    public boolean lifeIsTurned(int seat, boolean cameraAlreadyTurnedTheFelt) {
        return !cameraAlreadyTurnedTheFelt && isTurned(seat);
    }

    /**
     * The end of a counter that means this way, which is where its sign is written.
     * <p>The other half of the same rule: a view draws its minus here and takes its press
     * from {@link #lifeWayAt}, so the sign and the press cannot land on opposite ends.
     *
     * @param way -1 for the end that takes one off, 1 for the end that puts one on
     */
    public static Rect lifeEnd(Rect box, boolean turned, int way) {
        if (box.isEmpty() || way == 0) {
            return Rect.NONE;
        }
        boolean atTheLeft = (way < 0) != turned;
        int room = endRoom(box);
        return new Rect(atTheLeft ? box.x() : box.right() - room, box.y(), room, box.height());
    }

    /**
     * The room left for the number itself, between a counter's two ends.
     * <p>What the ends leave, not a share worked out separately: given their own halves the
     * three come to more than the whole, and the signs run into the digits.
     */
    public static Rect lifeMiddle(Rect box) {
        if (box.isEmpty()) {
            return Rect.NONE;
        }
        int room = endRoom(box);
        return new Rect(box.x() + room, box.y(),
                Math.max(1, box.width() - room * 2), box.height());
    }

    private static int endRoom(Rect box) {
        return Math.max(1, (int) Math.round(box.width() * LIFE_END_ROOM));
    }

    /**
     * The margin down either side of a mat, which both the buttons and the zones sit inside.
     * <p>Off a card's height rather than off whatever is printed, so the two sides still
     * match on a mat too shallow to draw either at full size.
     */
    private int edgeMargin(int seat) {
        return Math.max(1, (int) Math.round(cardHeightOn(seat) * EDGE_MARGIN));
    }

    /** How much of a mat's depth a run printed down its side has to fit into. */
    private double usableHeight(Rect mat, int seat) {
        return Math.max(1, mat.height() - edgeMargin(seat) * 2.0);
    }

    /** The line round the whole run of verb buttons, drawn as one panel on the felt. */
    public Rect verbGroup(int seat, int count) {
        Rect first = verbSlot(seat, 0, count);
        Rect last = verbSlot(seat, count - 1, count);
        if (first.isEmpty() || last.isEmpty()) {
            return Rect.NONE;
        }
        int gap = Math.max(1, (int) Math.round(first.height() * VERB_GAP));
        int top = Math.min(first.y(), last.y()) - gap;
        int bottom = Math.max(first.bottom(), last.bottom()) + gap;
        return new Rect(first.x() - gap, top, first.width() + gap * 2, bottom - top);
    }

    /**
     * Where a zone's name is printed on the mat, beside its slot.
     * <p>Not inside it: a slot is one card wide, and no word longer than "Exile" fits across
     * a card at two-player size, so a name in the slot arrives as "Grav...". Beside it is bare
     * felt, which is where a printed playmat puts it too. On the mat side of the column, so
     * the writing runs into the table rather than off the edge whichever chair it belongs to.
     */
    public Rect pileLabel(int seat, int index, int count) {
        Rect slot = pileSlot(seat, index, count);
        if (slot.isEmpty()) {
            return Rect.NONE;
        }
        // The command slots are one zone drawn as two boxes, so they are named once: twice
        // down the same column reads as two zones nobody bothered to name differently.
        int firstCommand = Zone.PILES_WITHOUT_A_COMMAND_ZONE;
        boolean commandSlot = count > firstCommand && index >= firstCommand;
        if (commandSlot && index > firstCommand) {
            return Rect.NONE;
        }
        Rect mat = matOf(seat);
        // Clear of the line round the group, not just of the slot: the name sits flush
        // against the column, so a gap measured to the slot alone puts the last letter of the
        // longest name under that line.
        int gap = Math.max(2, (int) Math.round(slot.height() * PILE_GAP * (1 + PILE_GROUP_PAD)));
        int width = Math.max(1, (int) Math.round(slot.width() * PILE_LABEL_WIDTHS));
        int height = Math.max(1, (int) Math.round(slot.height() * PILE_LABEL_HEIGHT));
        // Centered on whatever it names - its own slot, or the run of command slots - and
        // measured off the same pileSlot arithmetic that placed them.
        Rect last = commandSlot ? pileSlot(seat, count - 1, count) : slot;
        int spanTop = Math.min(slot.y(), last.y());
        int spanBottom = Math.max(slot.bottom(), last.bottom());
        int top = spanTop + (spanBottom - spanTop - height) / 2;
        int left = isTurned(seat) ? slot.right() + gap : slot.x() - gap - width;
        Rect label = new Rect(left, top, width, height);
        // A mat shared by three seats is narrow enough that the word starts off the side of
        // it, and a name half on the felt is worse than no name.
        return mat.contains(label.x(), label.y()) && mat.contains(label.right(), label.bottom())
                ? label
                : Rect.NONE;
    }

    /**
     * How tall a pile's count is written, in surface units.
     * <p>Exactly as tall as the pile's own name, so the two read as one label rather than a
     * word and a footnote. From its own constant it came out half the size, making the number
     * read most often - how much library is left - the smallest thing on the board.
     * <p>Still the name's height on the narrow mats with no room to write a name at all: the
     * count is written on the slot itself and does not need the room the name could not find.
     */
    public int pileCountHeight(int seat, int index, int count) {
        Rect slot = pileSlot(seat, index, count);
        if (slot.isEmpty()) {
            return 0;
        }
        return Math.max(1, (int) Math.round(slot.height() * PILE_LABEL_HEIGHT));
    }

    /**
     * The band across the foot of a slot where a commander's tax is written.
     * <p>A command slot holds one commander, so a count under it says "1" all game. It says
     * the tax instead - the number a Commander deck actually reads off that box - and pressing
     * it records another cast. Across the whole slot rather than in a corner: a number you
     * press has to be big enough to press.
     * <p>Taken from the slot it is handed rather than from the seat, because the two views
     * measure a slot in different spaces and the seated camera flips the y axis. In surface
     * units the band lands at the top of the slot on screen.
     *
     * @param slot the slot, in whatever space its view measures rectangles in
     * @return the band, or {@link Rect#NONE} for a slot too small to write a number on
     */
    public static Rect taxBand(Rect slot) {
        if (slot.isEmpty()) {
            return Rect.NONE;
        }
        int height = Math.max(1, (int) Math.round(slot.height() * TAX_BAND));
        return new Rect(slot.x(), slot.bottom() - height, slot.width(), height);
    }

    /**
     * How tall an ante card is drawn, as a share of the whole surface.
     * <p>Smaller than a card in play: the pot is looked at rather than reached into, so it
     * reads as an object on the table rather than competing with the board.
     */
    private static final double POT_CARD_HEIGHT = 0.15;

    /** The gap between cards in the pot, as a share of one card's width. */
    private static final double POT_GAP = 0.12;

    /** The most of the table's width the pot may take before its cards start overlapping. */
    private static final double POT_ACROSS = 0.6;

    /** How much taller the pot's tray is than the cards in it, for the label under them. */
    private static final double POT_LABEL = 0.34;

    /**
     * Where the pot sits: a row of cards across the middle of the table.
     * <p>The middle, because it is the one thing on the surface belonging to nobody - every
     * other rectangle here is somebody's mat, zone or life box.
     * <p>The middle is not empty, though: the life boxes sit on that same strip, so a pot
     * drawn centered lands on one. It takes the widest clear span instead, which is usually
     * still the middle and never on top of a number somebody has to read.
     * <p>Empty when there is nothing in it, so a table not playing for keeps has no space set
     * aside for a thing that will never appear.
     */
    public Rect pot(int howMany) {
        if (howMany <= 0 || width <= 0 || height <= 0) {
            return Rect.NONE;
        }
        int cardHeight = Math.max(1, (int) Math.round(height * POT_CARD_HEIGHT));
        int cardWidth = Math.max(1, CardShape.widthFor(cardHeight));
        int gap = Math.max(1, (int) Math.round(cardWidth * POT_GAP));

        int wanted = howMany * cardWidth + (howMany - 1) * gap;
        int room = Math.max(cardWidth, (int) Math.round(width * POT_ACROSS));
        // Too many to lay out side by side, so they lean instead: the row keeps its width and
        // the cards overlap, the way a pile pushed together on a table does.
        int across = Math.min(wanted, room);

        int trayHeight = cardHeight + (int) Math.round(cardHeight * POT_LABEL);
        int top = (height - trayHeight) / 2;
        Span clear = widestClearSpan(top, trayHeight);
        if (clear.width() < cardWidth) {
            // Nowhere on this table the pot could go without covering something somebody has
            // to read. Nothing is drawn rather than something drawn over a life total.
            return Rect.NONE;
        }
        across = Math.min(across, clear.width());
        return new Rect(clear.from() + (clear.width() - across) / 2, top, across, cardHeight);
    }

    /**
     * The whole space the pot takes, cards and the label under them.
     * <p>What is checked for room is what is drawn: a tray checked at the size of its cards
     * and then drawn taller is a tray that overlaps something nobody tested against.
     */
    public static Rect potTray(Rect pot) {
        if (pot.isEmpty()) {
            return Rect.NONE;
        }
        int trayHeight = pot.height() + (int) Math.round(pot.height() * POT_LABEL);
        return new Rect(pot.x(), pot.y(), pot.width(), trayHeight);
    }

    /** A run of table with nothing drawn on it. */
    private record Span(int from, int to) {

        int width() {
            return Math.max(0, to - from);
        }
    }

    /**
     * The widest stretch of a horizontal band with none of the mats' furniture on it.
     * <p>Life boxes, zone columns and verb runs: everything a mat puts near its own edges,
     * which is where a band across the middle of the table meets them.
     */
    private Span widestClearSpan(int top, int tall) {
        Rect band = new Rect(0, top, width, tall);
        List<Rect> blockers = new java.util.ArrayList<>();
        for (int seat = 0; seat < mats.size(); seat++) {
            add(blockers, band, lifeBox(seat));
            add(blockers, band, verbGroup(seat, TableVerb.count()));
            for (int count = Zone.PILES.size(); count >= 1; count--) {
                add(blockers, band, pileGroup(seat, 0, count - 1, count));
                add(blockers, band, pileLabel(seat, 0, count));
            }
        }
        blockers.sort(java.util.Comparator.comparingInt(Rect::x));

        Span best = new Span(0, 0);
        int from = 0;
        for (Rect blocked : blockers) {
            if (blocked.x() - from > best.width()) {
                best = new Span(from, blocked.x());
            }
            from = Math.max(from, blocked.right());
        }
        if (width - from > best.width()) {
            best = new Span(from, width);
        }
        return best;
    }

    private static void add(List<Rect> blockers, Rect band, Rect what) {
        if (!what.isEmpty() && what.overlaps(band)) {
            blockers.add(what);
        }
    }

    /**
     * Where one card of the pot goes.
     * <p>Spread when there is room and leaning when there is not, which falls out of dividing
     * the row by the gaps between cards rather than by the cards: with one card there are no
     * gaps and it takes the whole row, and with twenty the step is smaller than a card and
     * they overlap.
     */
    public static Rect potSlot(Rect pot, int index, int howMany) {
        if (pot.isEmpty() || howMany <= 0 || index < 0 || index >= howMany) {
            return Rect.NONE;
        }
        int cardWidth = Math.max(1, CardShape.widthFor(pot.height()));
        if (howMany == 1) {
            return new Rect(pot.x() + (pot.width() - cardWidth) / 2, pot.y(),
                    cardWidth, pot.height());
        }
        int step = Math.max(1, (pot.width() - cardWidth) / (howMany - 1));
        return new Rect(pot.x() + index * step, pot.y(), cardWidth, pot.height());
    }

    /**
     * The line across a mat that marks off the row nearest its own player.
     * <p>Where the lands go, on every playmat ever printed. A mat with nothing on it is
     * otherwise a rectangle, and a rectangle does not tell a player where to put their first
     * land - which is a question every game starts with. The line is a marking, not a rule:
     * nothing stops anybody putting anything either side of it.
     * <p>It starts past the zone column rather than running under it, because a line through a
     * graveyard reads as part of the graveyard.
     *
     * @param count how many zones the column holds, which is what sets its width
     */
    public Rect matDivider(int seat, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty()) {
            return Rect.NONE;
        }
        int band = (int) Math.round(cardHeightOn(seat) * LANDS_ROW);
        int thickness = Math.max(1, (int) Math.round(cardHeightOn(seat) * DIVIDER_THICKNESS));
        if (band * 2 + thickness > mat.height()) {
            // A mat squeezed between three seats has no room for rows, and a line across the
            // middle of one would be a line and not a marking.
            return Rect.NONE;
        }
        // The line stops short of everything printed down the sides of the mat: the zone
        // column, the names beside it, and the run of verb buttons opposite. Ending it at the
        // zone column alone drew it straight through whichever name it was level with and
        // then, once there were buttons too, through those - which reads as a zone or a
        // button crossed out rather than as a row marked off.
        Rect column = pileGroup(seat, 0, Math.max(0, count - 1), count);
        Rect named = pileLabel(seat, 0, count);
        Rect verbs = verbGroup(seat, TableVerb.count());
        int margin = Math.max(1, (int) Math.round(cardHeightOn(seat) * PILE_GAP));
        int columnStart = named.isEmpty() ? column.x() : Math.min(column.x(), named.x());
        int columnEnd = named.isEmpty() ? column.right() : Math.max(column.right(), named.right());
        int from = column.isEmpty() ? mat.x()
                : (isTurned(seat) ? columnEnd + margin : mat.x());
        int to = column.isEmpty() ? mat.right()
                : (isTurned(seat) ? mat.right() : columnStart - margin);
        if (!verbs.isEmpty()) {
            // The buttons sit on the player's other hand, so they bound the line from the
            // opposite end to the zones.
            from = Math.max(from, isTurned(seat) ? from : verbs.right() + margin);
            to = Math.min(to, isTurned(seat) ? verbs.x() - margin : to);
        }
        int y = isTurned(seat) ? mat.y() + band : mat.bottom() - band - thickness;
        return from >= to ? Rect.NONE : new Rect(from, y, to - from, thickness);
    }

    /**
     * The box drawn round a run of the column, from one zone to another inclusive.
     * <p>Two of these: one round the three zones a hand lives in and one round the command
     * zone on its own. Asked of the same arithmetic that places the slots, so a border can
     * never end up round the wrong ones.
     */
    public Rect pileGroup(int seat, int fromIndex, int toIndex, int count) {
        Rect first = pileSlot(seat, fromIndex, count);
        Rect last = pileSlot(seat, toIndex, count);
        if (first.isEmpty() || last.isEmpty()) {
            return Rect.NONE;
        }
        int pad = Math.max(1, (int) Math.round(first.height() * PILE_GAP * PILE_GROUP_PAD));
        int top = Math.min(first.y(), last.y()) - pad;
        int bottom = Math.max(first.bottom(), last.bottom()) + pad;
        return new Rect(first.x() - pad, top, first.width() + pad * 2, bottom - top);
    }

    /**
     * Which of this seat's piles a surface point is on, or -1.
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

    /** A card's width on this seat's mat, in surface units. */
    public double cardWidthOn(int seat) {
        Rect mat = matOf(seat);
        return mat.isEmpty() ? 0 : mat.width() / (double) CARDS_ACROSS_A_MAT;
    }

    /** A card's height on this seat's mat, in surface units. */
    public double cardHeightOn(int seat) {
        return cardWidthOn(seat) * CARD_HEIGHT_UNITS / CARD_WIDTH_UNITS;
    }

    /**
     * Where a card going into or out of this seat's hand passes over the mat.
     * <p>A hand is not on the table - it is private, and belongs to its player rather than to
     * a place - so it has no slot to fly to. What it has is an edge: the one nearest its
     * player, where a real hand is held. A card drawn crosses that edge and stops being
     * something anybody can point at, which is exactly what happens to a card picked up off a
     * real table.
     * <p>The same edge for everybody, so a draw looks the same to the player making it and to
     * the three people watching. Only the player whose hand it is has anywhere for it to go
     * afterwards, and that is drawn by the screen rather than by the mat.
     */
    public Rect handEdge(int seat) {
        Rect mat = matOf(seat);
        if (mat.isEmpty()) {
            return Rect.NONE;
        }
        int height = Math.max(1, (int) Math.round(cardHeightOn(seat)));
        int width = Math.max(1, CardShape.widthFor(height));
        int middle = mat.x() + (mat.width() - width) / 2;
        // Just outside the mat's own near edge, so a card arriving there reads as leaving the
        // table rather than as landing on the lands row.
        int edge = isTurned(seat) ? mat.y() - height / 2 : mat.bottom() - height / 2;
        return new Rect(middle, edge, width, height);
    }
}
