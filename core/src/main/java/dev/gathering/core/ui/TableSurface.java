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

    /** And the wider one that sets the command zone apart from the other three. */
    private static final double PILE_GROUP_GAP = 0.45;

    /**
     * How far the box round a group sits outside the zones in it, as a share of the gap.
     *
     * <p>Not the whole gap: the column already sits one gap in from the edge of the mat, so a
     * box a full gap wider than its zones would have its line exactly on the mat's own border.
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
     * heights.
     *
     * <p>One number for both sides. They used to be worked out separately - the buttons from
     * the gap between buttons, the zone column from the gap between zones - and those two
     * gaps are shares of two different things, so a mat came out with a comfortable margin
     * down one side and its zone column jammed against the border on the other. Nobody would
     * report that; it just looks like the board was laid out by two people.
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
     *
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
     *
     * <p>Remembered rather than rebuilt. The table in the world lays this out on every frame
     * it is on screen, and a seat count is all it depends on - so a surface that was worked
     * out once was being worked out sixty times a second per table, allocating a map, two
     * arrays and a rectangle per seat each time, to arrive at the identical answer.
     *
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
     * <p>Outer meaning the player's own right hand, which is where a deck goes on every table
     * anybody has ever played at. Two players facing each other reach for their own libraries
     * in mirror image, so the column is at the east edge of the surface for one of them and
     * the west edge for the other - stating it against the table instead put both columns on
     * the same side, which is one player's right hand and the other player's left.
     *
     * <p><b>Card-shaped, exactly.</b> These used to be a share of the mat's width by a share
     * of its depth, which on a two-player table made each one wider than it was tall and drew
     * a library as a letterbox. A zone holds a stack of cards and has to be the shape of one,
     * or the card on top of it is stretched to fit a slot that is not card-shaped.
     *
     * <p><b>The command zone stands apart.</b> It is the last of the four and the furthest
     * from its player, with a gap between it and the other three - which is how the tables
     * people already play on lay it out, and it says the right thing: three zones a hand is in
     * and out of all game, and one it touches twice. A format with no commanders passes a
     * count of three and the box is simply not there.
     *
     * @param index which zone, nearest its own player first
     * @param count how many zones the column holds - four with a command zone, three without
     */
    public Rect pileSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        // A zone is a card, unless a column of them would be taller than the mat - which it is
        // on a two-player board, where a mat is twice as wide as it is deep. Then they shrink
        // together, which keeps them a set rather than letting the last one fall off the edge.
        boolean separated = count > Zone.PILES_WITHOUT_A_COMMAND_ZONE;
        double worth = count * (1 + PILE_GAP) + PILE_GAP + (separated ? PILE_GROUP_GAP : 0);
        // Fitted into the mat inside its margin rather than into the whole of it. Measured
        // against the full height the column filled the mat top to bottom, so a board whose
        // zones had to shrink to fit came out with its graveyard sitting on the mat's own
        // border - a margin down the sides and none at the ends reads as a printing mistake.
        int height = Math.min(
                (int) Math.round(cardHeightOn(seat)), (int) (usableHeight(mat, seat) / worth));
        height = Math.max(1, height);
        int width = Math.max(1, CardShape.widthFor(height));

        int gap = Math.max(1, (int) Math.round(height * PILE_GAP));
        int apart = separated ? Math.max(1, (int) Math.round(height * PILE_GROUP_GAP)) : 0;
        int step = height + gap;
        int total = count * step - gap + apart;
        int top = mat.y() + (mat.height() - total) / 2;
        // Zone nought sits nearest its own player, and the column runs away from them. Which
        // end of the mat that is depends on which chair the board belongs to.
        int slot = isTurned(seat) ? index : count - 1 - index;
        // The mat's own margin, which the run of buttons down the other side uses too. Taken
        // from the gap between zones it shrank whenever the zones did, so a column squeezed
        // onto a shallow mat ended up hard against the border while the buttons opposite kept
        // their room.
        int inset = edgeMargin(seat);
        int left = isTurned(seat) ? mat.x() + inset : mat.right() - inset - width;
        return new Rect(left, top + slot * step + (slot >= breakAt(seat, count) ? apart : 0),
                width, height);
    }

    /**
     * Which slot down the surface the gap sits in front of.
     *
     * <p>The command slots are the far end of the column from their own player, and which end
     * of the <em>mat</em> that is depends on the chair - so for one player the gap is before
     * them and for the one opposite it is after them.
     */
    private int breakAt(int seat, int count) {
        // However many command slots this table is drawing, which is however many the column
        // has beyond the three a hand reaches for. Written from the count rather than fixed
        // at one, because a table with two of them sets both apart, not just the last.
        int commandSlots = Math.max(0, count - Zone.PILES_WITHOUT_A_COMMAND_ZONE);
        return isTurned(seat) ? count - commandSlots : commandSlots;
    }

    /**
     * The line across a mat that marks off the row nearest its own player.
     *
     * <p>Where the lands go, on every playmat ever printed. A mat with nothing on it is
     * otherwise a rectangle, and a rectangle does not tell a player where to put their first
     * land - which is a question every game starts with. The line is a marking, not a rule:
     * nothing stops anybody putting anything either side of it.
     *
     * <p>It starts past the zone column rather than running under it, because a line through a
     * graveyard reads as part of the graveyard.
     *
     * @param count how many zones the column holds, which is what sets its width
     */
    /**
     * One of the buttons printed on a mat for the verbs a player uses every turn.
     *
     * <p>On the player's own left, mirroring the zone column on their right, because a real
     * playmat has both and because a board with no affordances on it at all is a rectangle of
     * felt that a new player has no reason to think they can click.
     *
     * <p>A card wide and well under half that tall. Not card-shaped and not square either:
     * they are labels you press, and the only thing a button needs room for is its own word.
     * Square ones were a card wide <em>and</em> a card wide tall, so four of them came to
     * three and a half card heights - most of the depth of a mat, given over to four words on
     * the side of a table whose whole point is the space in the middle. Flat, they take about
     * a third of that and read more like the strip of buttons a table simulator puts down the
     * edge of a board, which is the thing they are for being.
     *
     * <p>The width is what the writing needs, so it is the width that stays: a name is fitted
     * across a button and shrinks with it, and a button narrowed to save felt is a button
     * whose word stops being written at all.
     *
     * <p>Index nought sits nearest its own player, the same way the zone column runs.
     */
    public Rect verbSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        int width = Math.max(1, (int) Math.round(cardWidthOn(seat)));
        // Shorter still on a mat with no room for the run, the same way the zone column
        // shrinks together rather than letting its last box fall off the edge.
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
     *
     * <p>Not on the mat. A life total is a thing about a player rather than a thing on their
     * board, and the board is where cards go - a number printed in the middle of the play
     * area is a number somebody will put a land on top of. Past the far edge it sits in the
     * strip of table between the mats, which is where the counters go on a real table and
     * where a player facing their own board looks up to read somebody else's.
     *
     * <p>Which edge is "far" depends on the chair, the same way everything else on a mat
     * does, so both players find their own number between their board and the middle.
     *
     * <p>Empty when there is not room for it off the end of the mat - a table drawn with the
     * seats crammed together has no strip to put it in, and a number half under a mat is
     * worse than a number in the status row alone.
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
        // Room on the table for the whole of it, and not overlapping anybody else's mat -
        // which is what a seat with somebody sitting directly opposite and close up means.
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
     *
     * <p>What "your own board" means to a camera. The counter is deliberately not on the mat
     * - a number in the play area is a number somebody puts a land on - but it is still part
     * of a player's own board, and both views framed the mat alone at first. On the seated
     * board that put the number the game is played to just past the top of the window; on the
     * board drawn in the world it put it under the status row. One rule, asked by both.
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
     *
     * <p>Here rather than in whichever view took the click, because the halves are drawn as
     * well as pressed - the number has a minus over one end and a plus over the other - and a
     * board that draws the plus on the side that takes one off is worse than no button.
     *
     * <p>Given the box rather than the seat, because the two views measure one in different
     * spaces: pixels on the window, units of felt on the block. Read out of absolute surface
     * units it would answer about the wrong end of the counter on the seated board, whose
     * camera turns the felt round on its way to the screen.
     *
     * <p>And given whether the counter is drawn turned about, because that is the other thing
     * the two views differ on: the seated camera has already turned the felt to face its own
     * player, so a box arrives in their frame and this is false; the board in the world turns
     * each seat's own writing instead, so there it is that seat's facing.
     */
    public static int lifeWayAt(Rect box, boolean turned, double x, double y) {
        if (box.isEmpty() || !box.contains((int) Math.round(x), (int) Math.round(y))) {
            return 0;
        }
        boolean atTheLeft = x < box.centreX();
        return atTheLeft == turned ? 1 : -1;
    }

    /**
     * The end of a counter that means this way, which is where its sign is written.
     *
     * <p>The other half of the same rule. A view draws its minus here and takes its press
     * from {@link #lifeWayAt}, so the two cannot end up on opposite ends - which is what they
     * were, on the board drawn in the world, for every seat facing the other way: the sign
     * was turned round with the mat and the press was not, so the end marked plus took a life
     * off.
     *
     * @param way -1 for the end that takes one off, 1 for the end that puts one on
     */
    /**
     * Whether a seat's counter is drawn turned about, in a view that may already have turned
     * the felt.
     *
     * <p>The seated camera turns the whole surface to face whoever is looking, so a counter
     * arrives there already in their frame and nothing more is done to it. The board in the
     * world turns nothing: it writes each seat's own marks facing that seat, so there the
     * seat's facing is what decides. One method because the drawing and the press both have
     * to ask, and they were asking separately - which is how the end marked plus came to take
     * a life off on every seat facing the other way.
     */
    public boolean lifeIsTurned(int seat, boolean cameraAlreadyTurnedTheFelt) {
        return !cameraAlreadyTurnedTheFelt && isTurned(seat);
    }

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
     *
     * <p>What the ends leave, rather than a share of the box worked out separately. Given
     * halves of their own the three came to more than the whole - the number was allowed half
     * and each end better than a quarter - so on a board drawn small enough for two figures
     * to fill their allowance, the minus ran into the four and the plus into the nought.
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
     *
     * <p>Off a card's height rather than off whatever is being printed, so the two sides
     * match on a mat too shallow to draw either of them at full size - which is the case they
     * stopped matching in.
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
     *
     * <p>Not inside the slot. A slot is exactly one card wide, because that is what it holds,
     * and no word longer than "Exile" fits across a card at the size a whole two-player board
     * is drawn at - so a name written in the slot arrives as "Grav...". Beside it there is
     * bare felt and room for the word, which is also where a printed playmat puts it.
     *
     * <p>On the mat side of the column, so the writing runs into the table rather than off
     * the edge of it, whichever chair the mat belongs to.
     */
    public Rect pileLabel(int seat, int index, int count) {
        Rect slot = pileSlot(seat, index, count);
        if (slot.isEmpty()) {
            return Rect.NONE;
        }
        // The command slots are one zone drawn as two boxes, so they are named once. Written
        // beside each of them the mat said "Command" twice down the same column, which reads
        // as two zones that somebody forgot to give different names rather than as one zone
        // with room for a partner. A printed playmat labels the region, not each slot.
        int firstCommand = Zone.PILES_WITHOUT_A_COMMAND_ZONE;
        boolean commandSlot = count > firstCommand && index >= firstCommand;
        if (commandSlot && index > firstCommand) {
            return Rect.NONE;
        }
        Rect mat = matOf(seat);
        // Clear of the line drawn round the group of slots, not just of the slot itself. The
        // name is written flush against the column, so a gap measured to the slot put the
        // last letter of the longest name underneath that line - which on the board drawn in
        // the world is a letter with its right-hand half missing.
        int gap = Math.max(2, (int) Math.round(slot.height() * PILE_GAP * (1 + PILE_GROUP_PAD)));
        int width = Math.max(1, (int) Math.round(slot.width() * PILE_LABEL_WIDTHS));
        int height = Math.max(1, (int) Math.round(slot.height() * PILE_LABEL_HEIGHT));
        // Centred on whatever the name names: its own slot, or the run of command slots. The
        // span is measured off the same pileSlot arithmetic that placed them, so the one name
        // cannot drift away from the pair it belongs to.
        Rect last = commandSlot ? pileSlot(seat, count - 1, count) : slot;
        int spanTop = Math.min(slot.y(), last.y());
        int spanBottom = Math.max(slot.bottom(), last.bottom());
        int top = spanTop + (spanBottom - spanTop - height) / 2;
        int left = isTurned(seat) ? slot.right() + gap : slot.x() - gap - width;
        Rect label = new Rect(left, top, width, height);
        // A mat with three seats round it is narrow enough that the word would start off the
        // side of it, and a name half on the felt is worse than no name.
        return mat.contains(label.x(), label.y()) && mat.contains(label.right(), label.bottom())
                ? label
                : Rect.NONE;
    }

    /**
     * How tall a pile's count is written, in surface units.
     *
     * <p>Exactly as tall as the pile's own name, so the two read as one label rather than as
     * a word and a footnote. The count used to take its height from a separate constant that
     * came out about half the size, which made the number a player reads most often - how
     * much library is left - the smallest thing written anywhere on the board.
     *
     * <p>Falls back to the name's height as it would have been, for the narrow mats where
     * there is no room beside the slot to write a name at all: the count is written on the
     * slot itself and does not need the room the name could not find.
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
     *
     * <p>A command slot holds one commander, so a number under it counting cards says "1" for
     * the whole game - the one number in the column that tells a player nothing. It says the
     * tax instead, which is the number a Commander deck actually reads off that box, and
     * pressing it records another cast.
     *
     * <p>Across the whole slot rather than in its corner like a count, for two reasons that
     * point the same way: a number you press has to be big enough to press, and a number
     * shaped differently from every count on the board does not get read as one.
     *
     * <p>Taken from the slot it is handed rather than worked out from the seat, because the
     * two views measure a slot in different spaces - pixels on the screen, surface units on
     * the block - and the seated camera turns the felt round so that the player's own mat is
     * nearest them. Written in surface units the band came out at the top of the slot on
     * screen, which is what a y axis that has been flipped does to a rectangle that was
     * measured against the wrong end of it.
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
     *
     * <p>Smaller than a card in play. The pot is not a zone anybody reaches into - it sits
     * there being looked at for the whole game - so it wants to read as an object on the
     * table rather than compete with the board for the eye.
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
     *
     * <p>The middle, deliberately, and it is the one thing on the surface that belongs to
     * nobody. Every other rectangle here is somebody's - a mat, a zone, a life box - and the
     * pot is the opposite of that, which is why it goes where the mats meet.
     *
     * <p>But the middle is not empty. A seat's life box sits on the edge of its mat facing
     * the middle, which is the same strip of table, so a pot drawn centred lands straight on
     * top of one. It goes in the widest clear span of that strip instead, which is usually
     * still the middle and is never on top of a number somebody needs to read.
     *
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
     *
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
     *
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
     *
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
     *
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

    /**
     * Where a card going into or out of this seat's hand passes over the mat.
     *
     * <p>A hand is not on the table - it is private, and belongs to its player rather than to
     * a place - so it has no slot to fly to. What it has is an edge: the one nearest its
     * player, where a real hand is held. A card drawn crosses that edge and stops being
     * something anybody can point at, which is exactly what happens to a card picked up off a
     * real table.
     *
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
