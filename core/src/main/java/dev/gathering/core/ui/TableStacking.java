package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * Working out which cards are piled on which, so a pile looks like a pile.
 * <p>There is no physics here and cards have no thickness, which means two cards dropped on
 * the same spot would draw in exactly the same place and the one underneath would simply stop
 * existing as far as anybody can see. On a real table you can always tell: the edges show, the
 * stack sits a little proud of the felt, and a pile of four is visibly taller than a pile of
 * two. None of that is physics - it is just what a stack looks like - so it can be drawn.
 * <p>Two cards count as stacked when they are on nearly the same spot, not merely touching.
 * A card half-covering another one is beside it, and reads correctly already; a card dead on
 * top of another one is the case that needs help. {@link #TIGHT} is where the line goes, and
 * it is in table units so it means the same thing at every window size.
 * <p>The offsets this hands out are drawing offsets and nothing else. The card's real position
 * does not move - that is state, and shifting it would mean a card creeping across the table
 * every time somebody dropped another one near it. What matters is that whatever draws a card
 * and whatever decides what the cursor is pointing at both ask this, so they agree.
 */
public final class TableStacking {

    /**
     * How close two cards have to be to count as stacked, in table units.
     * <p>About a fiftieth of the table, which at any real window size is a few pixels: close
     * enough that the lower card would be almost entirely hidden.
     */
    public static final int TIGHT = TablePosition.SPAN / 50;

    /**
     * How far up and left each card of a stack is drawn from the one below it, as a fraction
     * of the card itself.
     * <p>A fraction rather than a number of pixels, because there are now two spaces a card
     * can be measured in and they differ by a factor of ten thousand: two pixels is a visible
     * lean on a screen and two units on the table is nothing at all, so a fixed step would
     * make every pile on the block look like a single card.
     */
    public static final double STEP = 0.035;

    /** Where the stagger stops, so a forty-card pile does not walk off the table. */
    public static final int MAX_DEPTH = 5;

    private TableStacking() {
    }

    /**
     * How many cards each one in this list is sitting on top of.
     * <p>The list is in stacking order, back to front, which is the order a zone keeps its
     * contents in. Only cards earlier in the list count: a card cannot be sitting on one that
     * is on top of it.
     * <p>A null position - a card the game has not put down - counts as nothing and is on
     * nothing, because it is not on the table to be under anything.
     */
    public static List<Integer> depths(List<TablePosition> positions) {
        return piles(positions).depths();
    }

    /**
     * Every card's depth, pile size and whether anything is on it, worked out in one pass.
     * <p>Asked one card at a time, each of those is a walk over the whole mat, so a mat of
     * two hundred permanents cost forty thousand comparisons per question per frame - and the
     * board asks all three, for every card, every frame. An audit counted it.
     * <p>So the cards are dropped into a grid of cells a little wider than {@link #TIGHT}. Two
     * cards close enough to be stacked are then in the same cell or the next one along, and
     * only those nine cells are ever compared. A table spread out the way people actually
     * spread one is linear; a single pile of forty is still forty against forty, because
     * every one of those really is on every other.
     * <p>The same answers as the one-card questions, not approximately the same: the test
     * beside this checks them against a plain walk over random boards.
     */
    public static Piles piles(List<TablePosition> positions) {
        int count = positions.size();
        int[] depths = new int[count];
        int[] sizes = new int[count];
        boolean[] buried = new boolean[count];
        java.util.Map<Long, List<Integer>> cells = new java.util.HashMap<>();
        int cell = TIGHT + 1;
        for (int index = 0; index < count; index++) {
            TablePosition here = positions.get(index);
            if (here == null) {
                continue;
            }
            int column = Math.floorDiv(here.x(), cell);
            int row = Math.floorDiv(here.y(), cell);
            for (int across = column - 1; across <= column + 1; across++) {
                for (int down = row - 1; down <= row + 1; down++) {
                    List<Integer> earlier = cells.get(cellKey(across, down));
                    if (earlier == null) {
                        continue;
                    }
                    for (int below : earlier) {
                        if (isStackedOn(here, positions.get(below))) {
                            depths[index]++;
                            sizes[index]++;
                            sizes[below]++;
                            buried[below] = true;
                        }
                    }
                }
            }
            // Itself, which the one-card count includes because a card is on its own spot.
            sizes[index]++;
            cells.computeIfAbsent(cellKey(column, row), key -> new ArrayList<>()).add(index);
        }
        return new Piles(depths, sizes, buried);
    }

    private static long cellKey(int column, int row) {
        return ((long) column << 32) ^ (row & 0xFFFFFFFFL);
    }

    /** The answers {@link #piles} works out, by index into the list it was given. */
    public static final class Piles {

        private final int[] depths;
        private final int[] sizes;
        private final boolean[] buried;

        private Piles(int[] depths, int[] sizes, boolean[] buried) {
            this.depths = depths;
            this.sizes = sizes;
            this.buried = buried;
        }

        /** How many cards this covers. */
        public int size() {
            return depths.length;
        }

        /** The same as {@link TableStacking#depths}, for one card. */
        public int depth(int index) {
            return depths[index];
        }

        /** The same as {@link TableStacking#pileSizeAt}. */
        public int pileSize(int index) {
            return sizes[index] > 1 ? sizes[index] : 0;
        }

        /** The same as {@link TableStacking#isBuriedAt}. */
        public boolean isBuried(int index) {
            return buried[index];
        }

        /** Every depth, in order. */
        public List<Integer> depths() {
            List<Integer> all = new ArrayList<>(depths.length);
            for (int depth : depths) {
                all.add(depth);
            }
            return List.copyOf(all);
        }
    }

    public static boolean isStackedOn(TablePosition above, TablePosition below) {
        return above != null && below != null
                && Math.abs(above.x() - below.x()) <= TIGHT
                && Math.abs(above.y() - below.y()) <= TIGHT;
    }

    /**
     * How far to draw a card from where it actually is, given what is under it.
     * <p>Up and left, because that is the direction a stack leans when it is lit from the top
     * left - the same direction the shadows fall.
     * <p>Measured against the card, so the same call answers for a board drawn on a window and
     * one drawn on a table two blocks across.
     */
    public static int offsetFor(int depth, int cardWidth) {
        return -(int) Math.round(shownDepth(depth) * STEP * Math.max(0, cardWidth));
    }

    /**
     * How deep a card of this depth is actually drawn, which is not how deep it is.
     * <p>The stagger runs out at {@link #MAX_DEPTH} and a pile does not, so a stack of nine
     * is drawn as a stack of five with a badge saying nine. Anything that has to sit above
     * such a pile has to be told the same number, or it works out its height from a depth the
     * pile never reached: the board drawn on the block lifted each card by its true depth
     * while offsetting it by its clamped one, so a card flying over a pile of six passed
     * underneath it.
     */
    public static int shownDepth(int depth) {
        return Math.min(Math.max(0, depth), MAX_DEPTH);
    }

    /**
     * How many cards are in the pile this one is the top of, or zero if it is not on a pile.
     * <p>A count rather than a depth, because a badge saying "4" on a stack of four is what
     * somebody wants to read, and the stagger runs out at {@link #MAX_DEPTH} while the pile
     * does not.
     */
    public static int pileSizeAt(List<TablePosition> positions, int index) {
        TablePosition here = positions.get(index);
        if (here == null) {
            return 0;
        }
        int size = 0;
        for (TablePosition other : positions) {
            if (isStackedOn(here, other)) {
                size++;
            }
        }
        return size > 1 ? size : 0;
    }

    /** Whether anything is drawn on top of the card at this index. */
    public static boolean isBuriedAt(List<TablePosition> positions, int index) {
        TablePosition here = positions.get(index);
        if (here == null) {
            return false;
        }
        for (int above = index + 1; above < positions.size(); above++) {
            if (isStackedOn(positions.get(above), here)) {
                return true;
            }
        }
        return false;
    }
}
