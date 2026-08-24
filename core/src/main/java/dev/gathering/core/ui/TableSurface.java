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

    /** How deep the row nearest a player is, in card heights: one card and a little air. */
    private static final double LANDS_ROW = 1.15;

    /** And how heavy the line marking it is, as a share of a card's height. */
    private static final double DIVIDER_THICKNESS = 0.012;

    /** The gap between verb buttons, as a share of one button. */
    private static final double VERB_GAP = 0.16;

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
        int height = Math.min(
                (int) Math.round(cardHeightOn(seat)), (int) (mat.height() / worth));
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
        // Two gaps in from the edge rather than one, because the column is drawn inside a
        // box now and a box a gap wide round a column a gap from the edge puts its line on the
        // mat's own border.
        int inset = gap * 2;
        int left = isTurned(seat) ? mat.x() + inset : mat.right() - inset - width;
        return new Rect(left, top + slot * step + (slot >= breakAt(seat, count) ? apart : 0),
                width, height);
    }

    /**
     * Which slot down the surface the gap sits in front of.
     *
     * <p>The command zone is the far end of the column from its own player, and which end of
     * the <em>mat</em> that is depends on the chair - so for one player the gap is before the
     * last slot and for the one opposite it is after the first.
     */
    private int breakAt(int seat, int count) {
        return isTurned(seat) ? count - 1 : 1;
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
     * felt that a new player has no reason to think they can click. Square rather than
     * card-shaped: they are buttons, not places a card goes, and looking like a card slot is
     * exactly the wrong promise.
     *
     * <p>Index nought sits nearest its own player, the same way the zone column runs.
     */
    public Rect verbSlot(int seat, int index, int count) {
        Rect mat = matOf(seat);
        if (mat.isEmpty() || count <= 0 || index < 0 || index >= count) {
            return Rect.NONE;
        }
        double worth = count * (1 + VERB_GAP) + VERB_GAP;
        int side = Math.max(1, Math.min(
                (int) Math.round(cardWidthOn(seat)), (int) (mat.height() / worth)));
        int gap = Math.max(1, (int) Math.round(side * VERB_GAP));
        int step = side + gap;
        int total = count * step - gap;
        int top = mat.y() + (mat.height() - total) / 2;
        int slot = isTurned(seat) ? index : count - 1 - index;
        int inset = gap * 2;
        int left = isTurned(seat) ? mat.right() - inset - side : mat.x() + inset;
        return new Rect(left, top + slot * step, side, side);
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
        Rect mat = matOf(seat);
        int gap = Math.max(1, (int) Math.round(slot.height() * PILE_GAP));
        int width = Math.max(1, (int) Math.round(slot.width() * PILE_LABEL_WIDTHS));
        int height = Math.max(1, (int) Math.round(slot.height() * PILE_LABEL_HEIGHT));
        int top = slot.y() + (slot.height() - height) / 2;
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
}
