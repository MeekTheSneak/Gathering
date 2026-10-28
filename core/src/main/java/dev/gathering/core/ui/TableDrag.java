package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Moving several cards at once without losing the shape they were in.
 * <p>Dragging one card is arithmetic too plain to need a home. Dragging six is not, because of
 * the edge: if each card clamps to the table on its own, a group shoved into a corner arrives
 * as a single pile, and the arrangement somebody spent the game building is gone. There is no
 * undo for "my board collapsed", because nothing illegal happened.
 * <p>So the group moves by one delta or it does not move that far. The delta is trimmed until
 * every card in it fits, which means a group pushed against an edge slides along that edge
 * instead of crumpling - the same thing that happens when you push a handful of real cards
 * into the side of a real table.
 */
public final class TableDrag {

    private TableDrag() {
    }

    /**
     * The most of a wanted move that the whole group can make together.
     * <p>Each axis is trimmed on its own, so a group that cannot go further right can still go
     * down. Returns {@code {dx, dy}}.
     */
    public static int[] groupDelta(List<TablePosition> positions, int wantedX, int wantedY) {
        return new int[] {
            trim(wantedX, minX(positions), maxX(positions)),
            trim(wantedY, minY(positions), maxY(positions)),
        };
    }

    /** Every position moved by the largest delta the whole group can manage. */
    public static List<TablePosition> movedTogether(
            List<TablePosition> positions, int wantedX, int wantedY) {
        int[] delta = groupDelta(positions, wantedX, wantedY);
        List<TablePosition> moved = new ArrayList<>(positions.size());
        for (TablePosition position : positions) {
            moved.add(position == null
                    ? null
                    : position.movedTo(position.x() + delta[0], position.y() + delta[1]));
        }
        // Not List.copyOf: a card the game has not put down has no position, and a null in
        // the list is that fact rather than a mistake to reject.
        return Collections.unmodifiableList(moved);
    }

    /**
     * How far a delta can go before the leading edge of the group leaves the table.
     * <p>An empty group can move as far as it likes, which is vacuously true and saves every
     * caller a check.
     */
    private static int trim(int wanted, int lowest, int highest) {
        if (lowest > highest) {
            return wanted;
        }
        if (wanted < 0) {
            return Math.max(wanted, -lowest);
        }
        return Math.min(wanted, TablePosition.SPAN - highest);
    }

    private static int minX(List<TablePosition> positions) {
        int lowest = Integer.MAX_VALUE;
        for (TablePosition position : positions) {
            if (position != null) {
                lowest = Math.min(lowest, position.x());
            }
        }
        return lowest == Integer.MAX_VALUE ? 1 : lowest;
    }

    private static int maxX(List<TablePosition> positions) {
        int highest = Integer.MIN_VALUE;
        for (TablePosition position : positions) {
            if (position != null) {
                highest = Math.max(highest, position.x());
            }
        }
        return highest == Integer.MIN_VALUE ? 0 : highest;
    }

    private static int minY(List<TablePosition> positions) {
        int lowest = Integer.MAX_VALUE;
        for (TablePosition position : positions) {
            if (position != null) {
                lowest = Math.min(lowest, position.y());
            }
        }
        return lowest == Integer.MAX_VALUE ? 1 : lowest;
    }

    private static int maxY(List<TablePosition> positions) {
        int highest = Integer.MIN_VALUE;
        for (TablePosition position : positions) {
            if (position != null) {
                highest = Math.max(highest, position.y());
            }
        }
        return highest == Integer.MIN_VALUE ? 0 : highest;
    }
}
