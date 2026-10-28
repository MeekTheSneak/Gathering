package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.TablePosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TableSpreadTest {

    @Test
    @DisplayName("nothing to spread is nowhere to put it")
    void nothingIsNowhere() {
        assertThat(TableSpread.positions(0)).isEmpty();
        assertThat(TableSpread.positions(-3)).isEmpty();
    }

    @Test
    @DisplayName("one card goes in the middle rather than against an edge")
    void oneGoesInTheMiddle() {
        TablePosition only = TableSpread.positions(1).get(0);
        assertThat(only.x()).isEqualTo(TablePosition.SPAN / 2);
        assertThat(only.y()).isEqualTo(TablePosition.SPAN / 2);
    }

    @Test
    @DisplayName("the same count gives the same board twice")
    void theSameCountGivesTheSameBoard() {
        assertThat(TableSpread.positions(17)).isEqualTo(TableSpread.positions(17));
    }

    @Property
    @net.jqwik.api.Label("every card gets a spot, and no two share one")
    void everyCardGetsItsOwnSpot(@ForAll @IntRange(min = 1, max = 120) int howMany) {
        List<TablePosition> spots = TableSpread.positions(howMany);
        assertThat(spots).hasSize(howMany);
        Set<String> seen = new HashSet<>();
        for (TablePosition spot : spots) {
            assertThat(seen.add(spot.x() + "," + spot.y()))
                    .as("two cards on one spot at " + spot)
                    .isTrue();
        }
    }

    @Property
    @net.jqwik.api.Label("nothing is put off the mat")
    void nothingGoesOffTheMat(@ForAll @IntRange(min = 1, max = 120) int howMany) {
        for (TablePosition spot : TableSpread.positions(howMany)) {
            assertThat(spot.x()).isBetween(TableSpread.MARGIN, TablePosition.SPAN - TableSpread.MARGIN);
            assertThat(spot.y()).isBetween(TableSpread.MARGIN, TablePosition.SPAN - TableSpread.MARGIN);
        }
    }

    @Property
    @net.jqwik.api.Label("the grid comes out wider than it is tall")
    void theGridIsWiderThanItIsTall(@ForAll @IntRange(min = 2, max = 120) int howMany) {
        int columns = TableSpread.columnsFor(howMany);
        int rows = (howMany + columns - 1) / columns;
        assertThat(columns).isGreaterThanOrEqualTo(rows);
    }
}
