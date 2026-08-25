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
    @DisplayName("tables stacked front to back seat nobody, and say so before they are built")
    void aBlockOfTablesSeatsNobody() {
        // A table with another one above or below it has lost one of the two edges the game
        // is played across. The players it could still take would be sitting at the sides,
        // reading their own boards sideways, and the screen a player sits down to knows two
        // ways up: its own and the one opposite. So the shape seats nobody, and the placement
        // that would make it is refused where somebody can be told why.
        Set<TableCell> block = Set.of(cell(0, 0), cell(1, 0), cell(0, 1), cell(1, 1));

        assertThat(TableCluster.seatsEverySide(block)).isFalse();
        assertThat(TableCluster.of(block).capacity()).isZero();
    }

    @Test
    @DisplayName("a line of tables is the shape that seats people")
    void aLineSeatsTwoPerTable() {
        for (int tables = 1; tables <= TableCluster.MAX_TABLES; tables++) {
            Set<TableCell> line = new LinkedHashSet<>();
            for (int x = 0; x < tables; x++) {
                line.add(cell(x, 0));
            }
            assertThat(TableCluster.seatsEverySide(line))
                    .describedAs("a line of %s seats everybody", tables)
                    .isTrue();
            assertThat(TableCluster.of(line).capacity())
                    .isEqualTo(tables * TableCluster.SEATS_PER_TABLE);
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
    @DisplayName("a T seats only the tables that still have both facing edges")
    void aTShapeSeatsOnlyItsLine() {
        // The stem has a table above it, so it has lost its north edge and seats nobody. The
        // three across still face north and south and seat two apiece. This shape cannot be
        // built any more, but one already in a world still has to load and still has to seat
        // the people it can rather than putting anybody sideways.
        TableCluster cluster = clusterOf(cell(0, 0), cell(-1, 0), cell(1, 0), cell(0, -1));

        assertThat(cluster.capacity()).isEqualTo(4);
        for (SeatAnchor seat : cluster.seats()) {
            assertThat(seat.side()).isIn(Side.NORTH, Side.SOUTH);
        }
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
    void aShapeThatSeatsEverySideSeatsTwoPerTable(@ForAll("shapes") Set<TableCell> shape) {
        TableCluster cluster = TableCluster.of(shape);

        if (TableCluster.seatsEverySide(shape)) {
            assertThat(cluster.capacity())
                    .isEqualTo(cluster.tableCount() * TableCluster.SEATS_PER_TABLE);
        }
        assertThat(cluster.capacity())
                .isBetween(0, TableCluster.MAX_TABLES * TableCluster.SEATS_PER_TABLE);
    }

    /**
     * Nobody is ever seated where their own board would read sideways.
     *
     * <p>The whole reason a shape that is not a line seats fewer people. Stated over every
     * shape rather than over the ones the placement rule allows, because a world built before
     * that rule existed still loads and still has to put nobody at a side.
     */
    @Property(tries = 2000)
    void noSeatIsEverOnASideEdge(@ForAll("shapes") Set<TableCell> shape) {
        for (SeatAnchor seat : TableCluster.of(shape).seats()) {
            assertThat(seat.side()).isIn(Side.NORTH, Side.SOUTH);
        }
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
    void everybodySitsOppositeSomebody(@ForAll("shapes") Set<TableCell> shape) {
        // Sitting across from your opponent is the shape of the game, and now that a table
        // only seats people when both its north and south edges are free, it is true of every
        // shape rather than only of the ones that could manage it. This used to skip the
        // shapes it could not promise anything about - a block of four, an L - which are
        // exactly the shapes that seat nobody now.
        TableCluster cluster = TableCluster.of(shape);

        for (SeatAnchor seat : cluster.seats()) {
            assertThat(cluster.seats())
                    .describedAs("nobody is sitting opposite %s", seat)
                    .contains(new SeatAnchor(seat.cell(), seat.side().opposite()));
        }
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
