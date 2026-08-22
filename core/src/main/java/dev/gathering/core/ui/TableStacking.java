package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * Working out which cards are piled on which, so a pile looks like a pile.
 *
 * <p>There is no physics here and cards have no thickness, which means two cards dropped on
 * the same spot would draw in exactly the same place and the one underneath would simply stop
 * existing as far as anybody can see. On a real table you can always tell: the edges show, the
 * stack sits a little proud of the felt, and a pile of four is visibly taller than a pile of
 * two. None of that is physics - it is just what a stack looks like - so it can be drawn.
 *
 * <p>Two cards count as stacked when they are on nearly the same spot, not merely touching.
 * A card half-covering another one is beside it, and reads correctly already; a card dead on
 * top of another one is the case that needs help. {@link #TIGHT} is where the line goes, and
 * it is in table units so it means the same thing at every window size.
 *
 * <p>The offsets this hands out are drawing offsets and nothing else. The card's real position
 * does not move - that is state, and shifting it would mean a card creeping across the table
 * every time somebody dropped another one near it. What matters is that whatever draws a card
 * and whatever decides what the cursor is pointing at both ask this, so they agree.
 */
public final class TableStacking {

    /**
     * How close two cards have to be to count as stacked, in table units.
     *
     * <p>About a fiftieth of the table, which at any real window size is a few pixels: close
     * enough that the lower card would be almost entirely hidden.
     */
    public static final int TIGHT = TablePosition.SPAN / 50;

    /** How far up and left each card of a stack is drawn from the one below it, in pixels. */
    public static final int STEP = 2;

    /** Where the stagger stops, so a forty-card pile does not walk off the table. */
    public static final int MAX_DEPTH = 5;

    private TableStacking() {
    }

    /**
     * How many cards each one in this list is sitting on top of.
     *
     * <p>The list is in stacking order, back to front, which is the order a zone keeps its
     * contents in. Only cards earlier in the list count: a card cannot be sitting on one that
     * is on top of it.
     *
     * <p>A null position - a card the game has not put down - counts as nothing and is on
     * nothing, because it is not on the table to be under anything.
     */
    public static List<Integer> depths(List<TablePosition> positions) {
        List<Integer> depths = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            depths.add(depthOf(positions, index));
        }
        return List.copyOf(depths);
    }

    private static int depthOf(List<TablePosition> positions, int index) {
        TablePosition here = positions.get(index);
        if (here == null) {
            return 0;
        }
        int under = 0;
        for (int below = 0; below < index; below++) {
            if (isStackedOn(here, positions.get(below))) {
                under++;
            }
        }
        return under;
    }

    public static boolean isStackedOn(TablePosition above, TablePosition below) {
        return above != null && below != null
                && Math.abs(above.x() - below.x()) <= TIGHT
                && Math.abs(above.y() - below.y()) <= TIGHT;
    }

    /**
     * How far to draw a card from where it actually is, given what is under it.
     *
     * <p>Up and left, because that is the direction a stack leans when it is lit from the top
     * left - the same direction the shadows fall.
     */
    public static int offsetFor(int depth) {
        return -Math.min(Math.max(0, depth), MAX_DEPTH) * STEP;
    }

    /**
     * How many cards are in the pile this one is the top of, or zero if it is not on a pile.
     *
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
