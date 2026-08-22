package dev.gathering.core.table;

/**
 * Where one seat is: which table, and which edge of it.
 *
 * <p>A registration point rather than a chair. Play happens in the GUI, so what this has to
 * get right is that there are the right number of them, that they are on the outside of the
 * cluster, and that they are in the same order every time the cluster is worked out - a seat
 * that renumbers itself when a chunk reloads is a player who lost their seat.
 */
public record SeatAnchor(TableCell cell, Side side) {
}
