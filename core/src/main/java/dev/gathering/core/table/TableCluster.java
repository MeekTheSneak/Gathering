package dev.gathering.core.table;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tables pushed together, and where the seats end up.
 *
 * <p>The design is the literal shop gesture: one table seats two facing each other, and when
 * more people turn up you push another table against it. Edge-adjacent tables become one
 * surface running one session, seating two more each, up to four tables and eight seats -
 * which lands on the pod sizes that matter, a 4-player Commander game on two tables and an
 * 8-player draft on four.
 *
 * <p>All of it is arithmetic on a handful of coordinates, so it lives here where it can be
 * checked against every shape a player can build. A cluster that miscounts its seats, or puts
 * one on an edge two tables share, is not something anybody would notice until four people
 * were standing around trying to sit down.
 */
public final class TableCluster {

    /** Four tables, eight seats. Past this a "table" is a hall, and the seats stop pairing. */
    public static final int MAX_TABLES = 4;

    /** Every table seats two, which is what makes capacity 2, 4, 6, 8. */
    public static final int SEATS_PER_TABLE = 2;

    private final List<TableCell> cells;
    private final List<SeatAnchor> seats;

    private TableCluster(List<TableCell> cells, List<SeatAnchor> seats) {
        this.cells = List.copyOf(cells);
        this.seats = List.copyOf(seats);
    }

    /**
     * The cluster reachable from one table.
     *
     * <p>{@code present} answers whether there is a table at a cell. Walking outward from the
     * one placed, rather than scanning a region, means the cost is the size of the cluster and
     * not the size of whatever the player has built nearby.
     */
    public static TableCluster around(TableCell start, java.util.function.Predicate<TableCell> present) {
        Set<TableCell> found = new LinkedHashSet<>();
        if (!present.test(start)) {
            return new TableCluster(List.of(), List.of());
        }

        // Uncapped on purpose. MAX_TABLES is a rule about what may be *built*, enforced where
        // a table is placed and can be explained; capping the search instead would return an
        // arbitrary subset that depended on which table the walk started from, and the same
        // cluster would answer differently to two different players standing at it.
        Deque<TableCell> pending = new ArrayDeque<>();
        pending.add(start);
        found.add(start);
        while (!pending.isEmpty()) {
            TableCell cell = pending.removeFirst();
            for (Side side : Side.values()) {
                TableCell neighbour = cell.step(side);
                if (!found.contains(neighbour) && present.test(neighbour)) {
                    found.add(neighbour);
                    pending.addLast(neighbour);
                }
            }
        }
        return of(found);
    }

    /**
     * A cluster from a known set of tables.
     *
     * <p>Ordered before anything is derived from it, so the same set of tables always produces
     * the same seats in the same order however it was discovered - a seat index that depends
     * on which table happened to load first is a seat that changes hands on a chunk reload.
     */
    /**
     * The shape a client should assume from nothing but a seat count.
     *
     * <p>A client is told a board, not a building: the session froze the cluster's shape when
     * it started, and what arrives on the wire is a list of seats. That is enough, because
     * seats come in facing pairs, so a row of {@code seats / 2} tables reproduces the seating
     * the server laid out.
     *
     * <p>Here rather than in whichever screen needs it, because the seated view and the table
     * in the world both have to work this out and two answers would be two different boards -
     * one of them behind the other by however long it took somebody to notice.
     */
    public static List<SeatAnchor> assumedSeating(int seatCount) {
        int tables = Math.max(0, seatCount + SEATS_PER_TABLE - 1) / SEATS_PER_TABLE;
        Set<TableCell> row = new LinkedHashSet<>();
        for (int x = 0; x < tables; x++) {
            row.add(new TableCell(x, 0));
        }
        // Built by asking the real thing rather than by reproducing what it does. Writing the
        // rule out a second time is how the client ended up seating everybody on the opposite
        // edge from the server: both sides were internally consistent and neither said so.
        return of(row).seats();
    }

    public static TableCluster of(Set<TableCell> cells) {
        List<TableCell> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.comparingInt(TableCell::z).thenComparingInt(TableCell::x));
        return new TableCluster(ordered, seatsFor(ordered));
    }

    public List<TableCell> cells() {
        return cells;
    }

    public List<SeatAnchor> seats() {
        return seats;
    }

    public int tableCount() {
        return cells.size();
    }

    public int capacity() {
        return seats.size();
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public boolean contains(TableCell cell) {
        return cells.contains(cell);
    }

    /**
     * Whether another table may join.
     *
     * <p>A cluster is capped rather than allowed to sprawl, so the answer to "can I push one
     * more table against this" has to be given at placement time, where it can be explained,
     * rather than silently by a table that sits next to a cluster without joining it.
     */
    public boolean hasRoomForAnotherTable() {
        return tableCount() < MAX_TABLES;
    }

    /**
     * Where the seats go: around the outside of the cluster, facing pairs first.
     *
     * <p>Every table adds two to the capacity, but the two are not necessarily on that table.
     * Push four tables into a T and the middle one has a single outward edge - two seats on it
     * would put somebody inside the furniture, and dropping one would make a four-table
     * cluster seat seven. So the count is per cluster and the placement is around its
     * perimeter, which is what the design says and what pushed-together tables look like.
     *
     * <p>Facing pairs are taken first, because sitting opposite your opponent is the shape of
     * the game and a table with two free opposite edges should use them. What is left over is
     * filled from the remaining outward edges in a fixed order, so the same cluster always
     * seats people in the same places.
     */
    private static List<SeatAnchor> seatsFor(List<TableCell> ordered) {
        Set<TableCell> present = new HashSet<>(ordered);
        int wanted = ordered.size() * SEATS_PER_TABLE;

        List<SeatAnchor> anchors = new ArrayList<>(wanted);
        for (TableCell cell : ordered) {
            List<Side> outward = outwardSides(cell, present);
            // North and south only. A seat on an east or west edge is a board that has to be
            // read sideways, and the screen a player sits down to knows two ways up: its own
            // and the one opposite. Rather than teach it a quarter turn nobody would ever see
            // - tables join in a line, and a shape that would need such a seat is refused
            // where it is placed and can be explained - the edge simply seats nobody.
            if (!outward.contains(Side.NORTH) || !outward.contains(Side.SOUTH)) {
                continue;
            }
            anchors.add(new SeatAnchor(cell, Side.NORTH));
            anchors.add(new SeatAnchor(cell, Side.SOUTH));
        }
        return anchors.size() > wanted
                ? List.copyOf(anchors.subList(0, wanted))
                : List.copyOf(anchors);
    }

    /**
     * Whether every table in this shape would seat people on facing north and south edges.
     *
     * <p>Which is to say: whether it is a line. A table with another one above or below it
     * has lost one of the two edges the game is played across, and the players it could still
     * seat would be sitting at the sides reading their own boards sideways. Asked where a
     * table is placed, so the answer arrives as a refusal somebody can be told about rather
     * than as a seat that turns out to be unusable.
     */
    public static boolean seatsEverySide(Set<TableCell> cells) {
        if (cells.isEmpty()) {
            return true;
        }
        for (TableCell cell : cells) {
            if (cells.contains(cell.step(Side.NORTH)) || cells.contains(cell.step(Side.SOUTH))) {
                return false;
            }
        }
        return true;
    }

    private static List<Side> outwardSides(TableCell cell, Set<TableCell> present) {
        List<Side> outward = new ArrayList<>(4);
        for (Side side : Side.values()) {
            if (!present.contains(cell.step(side))) {
                outward.add(side);
            }
        }
        return outward;
    }

    private static List<Side> facingPair(List<Side> outward) {
        for (Side[] pair : Side.FACING_PAIRS) {
            if (outward.contains(pair[0]) && outward.contains(pair[1])) {
                return List.of(pair[0], pair[1]);
            }
        }
        return null;
    }
}
