package dev.gathering.core.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every shape somebody can push four tables into.
 *
 * <p>Seats are the thing a player notices immediately and the thing this arithmetic is most
 * able to get quietly wrong: two seats on the edge two tables share, a cluster that seats
 * seven, an eighth seat that appears and disappears as chunks load. So the shapes are
 * enumerated rather than sampled - there are few enough of them - and the invariants are
 * stated over all of them at once.
 */
class TableClusterTest {

    @Test
    @DisplayName("one table seats two, facing each other")
    void oneTableSeatsTwoFacing() {
        TableCluster cluster = clusterOf(cell(0, 0));

        assertThat(cluster.tableCount()).isEqualTo(1);
        assertThat(cluster.capacity()).isEqualTo(2);
        assertThat(sidesOf(cluster)).containsExactlyInAnyOrder(Side.NORTH, Side.SOUTH);
    }

    @Test
    @DisplayName("two tables pushed together seat four, still in facing pairs")
    void twoTablesSeatFour() {
        // The 4-player Commander pod, and the draft pick-2 breakpoint.
        TableCluster cluster = clusterOf(cell(0, 0), cell(1, 0));

        assertThat(cluster.capacity()).isEqualTo(4);
        assertThat(sidesOf(cluster)).containsExactly(Side.NORTH, Side.SOUTH, Side.NORTH, Side.SOUTH);
    }

    @Test
    @DisplayName("four tables in a block seat eight, around the outside")
    void fourTablesSeatEight() {
        TableCluster cluster = clusterOf(cell(0, 0), cell(1, 0), cell(0, 1), cell(1, 1));

        assertThat(cluster.capacity()).isEqualTo(8);
        // Every corner table has two outward edges and no facing pair, so it seats two people
        // round the outside corner - which is what pushed-together tables actually look like.
        for (SeatAnchor seat : cluster.seats()) {
            assertThat(cluster.contains(seat.cell().step(seat.side())))
                    .describedAs("seat %s faces into another table", seat)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a full cluster says so, rather than the search quietly stopping short")
    void aFullClusterHasNoRoom() {
        // The cap is a rule about what may be built, enforced where a table is placed and can
        // be explained. Capping the search instead would return whichever four tables the
        // walk happened to reach first, and the same cluster would answer differently to two
        // players standing at opposite ends of it.
        Set<TableCell> four = new LinkedHashSet<>(
                List.of(cell(0, 0), cell(1, 0), cell(2, 0), cell(3, 0)));

        TableCluster cluster = TableCluster.around(cell(0, 0), four::contains);

        assertThat(cluster.tableCount()).isEqualTo(TableCluster.MAX_TABLES);
        assertThat(cluster.capacity()).isEqualTo(8);
        assertThat(cluster.hasRoomForAnotherTable()).isFalse();
    }

    @Test
    @DisplayName("four tables in a T still seat eight, around the perimeter")
    void aTShapeStillSeatsEight() {
        // The middle table has one outward edge. Two seats on it would put somebody inside
        // the furniture; one would make a four-table cluster seat seven.
        TableCluster cluster = clusterOf(cell(0, 0), cell(-1, 0), cell(1, 0), cell(0, -1));

        assertThat(cluster.capacity()).isEqualTo(8);
    }

    @Test
    @DisplayName("tables a gap apart are two clusters, not one")
    void tablesThatDoNotTouchDoNotMerge() {
        Set<TableCell> apart = Set.of(cell(0, 0), cell(2, 0));

        assertThat(TableCluster.around(cell(0, 0), apart::contains).tableCount()).isEqualTo(1);
        assertThat(TableCluster.around(cell(2, 0), apart::contains).tableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("diagonally touching tables are two clusters: a corner is not an edge")
    void diagonalNeighboursDoNotMerge() {
        Set<TableCell> diagonal = Set.of(cell(0, 0), cell(1, 1));

        assertThat(TableCluster.around(cell(0, 0), diagonal::contains).tableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same tables always produce the same seats in the same order")
    void seatOrderIsStable() {
        // A seat index that depends on which table happened to load first is a player who
        // loses their seat to a chunk reload.
        Set<TableCell> forwards = new LinkedHashSet<>(List.of(cell(0, 0), cell(1, 0), cell(1, 1)));
        Set<TableCell> backwards = new LinkedHashSet<>(List.of(cell(1, 1), cell(1, 0), cell(0, 0)));

        assertThat(TableCluster.of(forwards).seats()).isEqualTo(TableCluster.of(backwards).seats());
        assertThat(TableCluster.around(cell(0, 0), forwards::contains).seats())
                .isEqualTo(TableCluster.around(cell(1, 1), forwards::contains).seats());
    }

    @Property(tries = 2000)
    void everyShapeSeatsTwoPerTable(@ForAll("shapes") Set<TableCell> shape) {
        TableCluster cluster = TableCluster.of(shape);

        assertThat(cluster.capacity())
                .isEqualTo(cluster.tableCount() * TableCluster.SEATS_PER_TABLE);
        assertThat(cluster.capacity()).isBetween(2, TableCluster.MAX_TABLES * TableCluster.SEATS_PER_TABLE);
    }

    @Property(tries = 2000)
    void noSeatIsEverOnAnEdgeTwoTablesShare(@ForAll("shapes") Set<TableCell> shape) {
        // Somebody sitting there would be inside the furniture.
        TableCluster cluster = TableCluster.of(shape);

        for (SeatAnchor seat : cluster.seats()) {
            assertThat(shape).doesNotContain(seat.cell().step(seat.side()));
        }
    }

    @Property(tries = 2000)
    void noTwoSeatsAreInTheSamePlace(@ForAll("shapes") Set<TableCell> shape) {
        assertThat(TableCluster.of(shape).seats()).doesNotHaveDuplicates();
    }

    @Property(tries = 2000)
    void everySeatIsOnATableInThisCluster(@ForAll("shapes") Set<TableCell> shape) {
        TableCluster cluster = TableCluster.of(shape);

        for (SeatAnchor seat : cluster.seats()) {
            assertThat(cluster.contains(seat.cell())).isTrue();
        }
    }

    @Property(tries = 2000)
    void peopleSitOppositeEachOtherWhereverTheShapeAllowsIt(@ForAll("shapes") Set<TableCell> shape) {
        // Sitting across from your opponent is the shape of the game, so where every table
        // has a free pair of opposite edges - a single table, or any straight run of them -
        // every seat should have somebody facing it. Shapes that cannot manage that, like a
        // block of four, are the ones this deliberately does not ask about.
        TableCluster cluster = TableCluster.of(shape);
        boolean everyTableCanPair = cluster.cells().stream()
                .allMatch(cell -> hasFullyOutwardFacingPair(cell, shape));
        if (!everyTableCanPair) {
            return;
        }

        for (SeatAnchor seat : cluster.seats()) {
            assertThat(cluster.seats())
                    .describedAs("nobody is sitting opposite %s", seat)
                    .contains(new SeatAnchor(seat.cell(), seat.side().opposite()));
        }
    }

    private static boolean hasFullyOutwardFacingPair(TableCell cell, Set<TableCell> shape) {
        for (Side[] pair : Side.FACING_PAIRS) {
            if (!shape.contains(cell.step(pair[0])) && !shape.contains(cell.step(pair[1]))) {
                return true;
            }
        }
        return false;
    }

    @Property(tries = 2000)
    void walkingOutFromAnyTableFindsTheSameCluster(@ForAll("shapes") Set<TableCell> shape) {
        TableCluster fromFirst = TableCluster.around(shape.iterator().next(), shape::contains);

        for (TableCell cell : shape) {
            assertThat(TableCluster.around(cell, shape::contains).cells()).isEqualTo(fromFirst.cells());
        }
    }

    /** Connected shapes of one to four tables, which is every cluster that can exist. */
    @Provide
    Arbitrary<Set<TableCell>> shapes() {
        return Arbitraries.integers().between(1, TableCluster.MAX_TABLES)
                .flatMap(size -> Arbitraries.integers().between(0, 3).list().ofSize((size - 1) * 2)
                        .map(steps -> grow(size, steps)));
    }

    @Test
    @DisplayName("what a client assumes from a seat count is what the cluster actually laid out")
    void assumedSeatingMatchesARowOfTables() {
        // A client is told a board, not a building, so it rebuilds the seating from the seat
        // count alone. If that ever stops agreeing with the real thing, the screen and the
        // table in the world put everybody's playmat somewhere the server never put it - and
        // nothing would say so, because both sides would be internally consistent.
        for (int tables = 1; tables <= TableCluster.MAX_TABLES; tables++) {
            Set<TableCell> row = new LinkedHashSet<>();
            for (int x = 0; x < tables; x++) {
                row.add(cell(x, 0));
            }
            List<SeatAnchor> real = TableCluster.of(row).seats();

            assertThat(TableCluster.assumedSeating(real.size()))
                    .describedAs("%s tables in a row", tables)
                    .isEqualTo(real);
        }
    }

    @Test
    @DisplayName("assuming a seating for nobody is a seating for nobody")
    void assumedSeatingHandlesNothing() {
        assertThat(TableCluster.assumedSeating(0)).isEmpty();
        assertThat(TableCluster.assumedSeating(-4)).isEmpty();
    }

    /** Grows a connected blob by repeatedly stepping off a cell already in it. */
    private static Set<TableCell> grow(int size, List<Integer> steps) {
        Set<TableCell> cells = new LinkedHashSet<>();
        cells.add(cell(0, 0));
        int step = 0;
        while (cells.size() < size && step < steps.size()) {
            List<TableCell> current = List.copyOf(cells);
            TableCell from = current.get(steps.get(step) % current.size());
            cells.add(from.step(Side.values()[steps.get(step) % Side.values().length]));
            step++;
        }
        return cells;
    }

    private static TableCluster clusterOf(TableCell... cells) {
        return TableCluster.around(cells[0], new HashSet<>(List.of(cells))::contains);
    }

    private static List<Side> sidesOf(TableCluster cluster) {
        return cluster.seats().stream().map(SeatAnchor::side).toList();
    }

    private static TableCell cell(int x, int z) {
        return new TableCell(x, z);
    }
}
