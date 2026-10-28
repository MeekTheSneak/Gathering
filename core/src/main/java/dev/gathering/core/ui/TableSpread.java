package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * Where to put a handful of cards so they can all be seen at once.
 * <p>For the one thing a scripted board is for: filling a mat up so somebody can look at what
 * a real game's worth of cards actually does to the layout. A player arranges their own board
 * and would not thank anything for doing it for them - this exists so that "the cards render
 * tiny on a crowded table" can be reproduced without playing forty cards by hand first.
 * <p>A grid, wider than it is tall because a mat is, inset from the edges so no card hangs
 * off. Cards never share a spot: a stack of forty is one card as far as looking at it goes,
 * and looking at it is the whole point.
 */
public final class TableSpread {

    /** How far in from the edge of a mat the grid starts, in table units. */
    public static final int MARGIN = TablePosition.SPAN / 8;

    /** Roughly how much wider than tall a row of cards should sit. */
    private static final double WIDER_THAN_TALL = 1.6;

    private TableSpread() {
    }

    /**
     * Somewhere for each of {@code howMany} cards, in reading order.
     * <p>Deterministic: the same count gives the same board twice, so two runs of a scripted
     * test photograph the same thing.
     */
    public static List<TablePosition> positions(int howMany) {
        if (howMany <= 0) {
            return List.of();
        }
        int columns = columnsFor(howMany);
        int rows = (howMany + columns - 1) / columns;
        int room = TablePosition.SPAN - MARGIN * 2;
        List<TablePosition> spots = new ArrayList<>(howMany);
        for (int index = 0; index < howMany; index++) {
            int column = index % columns;
            int row = index / columns;
            spots.add(TablePosition.of(
                    MARGIN + step(room, columns, column),
                    MARGIN + step(room, rows, row)));
        }
        return List.copyOf(spots);
    }

    /** How many across, for a grid that comes out wider than it is tall. */
    public static int columnsFor(int howMany) {
        int columns = (int) Math.ceil(Math.sqrt(Math.max(1, howMany) * WIDER_THAN_TALL));
        return Math.max(1, Math.min(Math.max(1, howMany), columns));
    }

    /**
     * Where the nth of {@code count} sits along a run of {@code room}.
     * <p>A single one goes in the middle rather than hard against the near edge, which is
     * what dividing by {@code count - 1} would do to it.
     */
    private static int step(int room, int count, int index) {
        return count <= 1 ? room / 2 : (int) Math.round((double) room * index / (count - 1));
    }
}
