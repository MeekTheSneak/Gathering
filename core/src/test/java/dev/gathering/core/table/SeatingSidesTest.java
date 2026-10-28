package dev.gathering.core.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How many chairs a table has, and which edges they are at.
 * <p>Two, facing each other, and it matters that it stays two: the seated view turns the whole
 * board around so the player's own mat is the near one, and "the near one" only means anything
 * while there are exactly two halves to be near.
 */
class SeatingSidesTest {

    @Test
    @DisplayName("one table seats two people, opposite each other")
    void oneTableHasTwoFacingSeats() {
        List<SeatAnchor> seats = TableCluster.of(Set.of(new TableCell(0, 0))).seats();

        assertThat(seats).hasSize(2);
        assertThat(seats.get(0).side().opposite())
                .describedAs("the two seats face each other rather than sharing a corner")
                .isEqualTo(seats.get(1).side());
    }

    @Test
    @DisplayName("the two edges of a single table are north and south, not east and west")
    void theSeatsAreTheNorthAndSouthEdges() {
        // Which pair it is has to be settled rather than incidental: the camera turns the board
        // around by asking whether the player's mat is the north half or the south one, and a
        // table that seated east and west instead would have both mats on the same half.
        List<Side> sides = TableCluster.of(Set.of(new TableCell(0, 0))).seats()
                .stream().map(SeatAnchor::side).toList();

        assertThat(sides).containsExactlyInAnyOrder(Side.NORTH, Side.SOUTH);
    }

    @Test
    @DisplayName("the east and west edges of a single table are not seats")
    void theOtherTwoEdgesAreNotSeats() {
        TableCluster cluster = TableCluster.of(Set.of(new TableCell(0, 0)));

        assertThat(cluster.seats()).doesNotContain(
                new SeatAnchor(new TableCell(0, 0), Side.EAST),
                new SeatAnchor(new TableCell(0, 0), Side.WEST));
    }
}
