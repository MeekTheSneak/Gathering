package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.TablePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Dragging a handful of cards without wrecking the formation.
 * <p>The failure worth guarding is at the edge of the table. If each card clamps on its own, a
 * group shoved into a corner arrives as a single pile - the board somebody spent the game
 * arranging, gone, with no undo because nothing illegal happened.
 */
class TableDragTest {

    @Nested
    @DisplayName("keeping the shape")
    class KeepingTheShape {

        @Test
        @DisplayName("a group in open table moves exactly as far as it was asked to")
        void anUnobstructedGroupMovesFully() {
            List<TablePosition> group = List.of(
                    TablePosition.of(1000, 1000), TablePosition.of(2000, 1500));

            List<TablePosition> moved = TableDrag.movedTogether(group, 500, -200);

            assertThat(moved).containsExactly(
                    TablePosition.of(1500, 800), TablePosition.of(2500, 1300));
        }

        @Test
        @DisplayName("a group stopped by the edge keeps its spacing")
        void theFormationSurvivesTheEdge() {
            // The leading card is 300 from the right edge, so the whole group moves 300 and
            // the gap between them is exactly what it was.
            List<TablePosition> group = List.of(
                    TablePosition.of(TablePosition.SPAN - 1300, 5000),
                    TablePosition.of(TablePosition.SPAN - 300, 5000));

            List<TablePosition> moved = TableDrag.movedTogether(group, 9000, 0);

            assertThat(moved.get(1).x() - moved.get(0).x()).isEqualTo(1000);
            assertThat(moved.get(1).x()).isEqualTo(TablePosition.SPAN);
        }

        @Test
        @DisplayName("blocked on one axis is not blocked on the other")
        void axesAreTrimmedSeparately() {
            List<TablePosition> group = List.of(TablePosition.of(TablePosition.SPAN, 4000));

            List<TablePosition> moved = TableDrag.movedTogether(group, 5000, 1000);

            assertThat(moved.get(0).x()).isEqualTo(TablePosition.SPAN);
            assertThat(moved.get(0).y()).isEqualTo(5000);
        }

        @Test
        @DisplayName("a group already against the edge does not move that way at all")
        void aPinnedGroupStaysPut() {
            List<TablePosition> group = List.of(
                    TablePosition.of(0, 1000), TablePosition.of(400, 1000));

            assertThat(TableDrag.groupDelta(group, -500, 0)).containsExactly(0, 0);
        }

        @Test
        @DisplayName("turning is not part of moving, so angles survive a drag")
        void anglesAreUntouched() {
            List<TablePosition> group = List.of(TablePosition.of(1000, 1000, 45));

            assertThat(TableDrag.movedTogether(group, 100, 100).get(0).rotation()).isEqualTo(45);
        }

        @Test
        @DisplayName("a card with no place on the table is carried along as nothing")
        void placelessCardsAreLeftAlone() {
            List<TablePosition> group = Arrays.asList(TablePosition.of(1000, 1000), null);

            List<TablePosition> moved = TableDrag.movedTogether(group, 100, 0);

            assertThat(moved.get(0)).isEqualTo(TablePosition.of(1100, 1000));
            assertThat(moved.get(1)).isNull();
        }

        @Test
        @DisplayName("dragging nothing anywhere is not an error")
        void anEmptyGroupIsFine() {
            assertThat(TableDrag.movedTogether(List.of(), 500, 500)).isEmpty();
            assertThat(TableDrag.groupDelta(List.of(), 500, 500)).containsExactly(500, 500);
        }
    }

    @Property(tries = 3000)
    void everyCardStaysOnTheTable(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 0, max = TablePosition.SPAN) Integer> xs,
            @ForAll @IntRange(min = -20_000, max = 20_000) int wantedX,
            @ForAll @IntRange(min = -20_000, max = 20_000) int wantedY) {
        List<TablePosition> group = new ArrayList<>();
        for (int x : xs) {
            group.add(TablePosition.of(x, x));
        }

        for (TablePosition moved : TableDrag.movedTogether(group, wantedX, wantedY)) {
            assertThat(moved.x()).isBetween(0, TablePosition.SPAN);
            assertThat(moved.y()).isBetween(0, TablePosition.SPAN);
        }
    }

    @Property(tries = 3000)
    void everyGapBetweenCardsIsExactlyWhatItWas(
            @ForAll @Size(min = 2, max = 8) List<@IntRange(min = 0, max = TablePosition.SPAN) Integer> xs,
            @ForAll @IntRange(min = -20_000, max = 20_000) int wantedX,
            @ForAll @IntRange(min = -20_000, max = 20_000) int wantedY) {
        // This is the whole point. Whatever the group is asked to do and however the edge
        // stops it, the cards keep the arrangement they were in - one delta for all of them,
        // or a smaller one, never a different one each.
        List<TablePosition> group = new ArrayList<>();
        for (int x : xs) {
            group.add(TablePosition.of(x, TablePosition.SPAN - x));
        }

        List<TablePosition> moved = TableDrag.movedTogether(group, wantedX, wantedY);

        for (int index = 1; index < group.size(); index++) {
            assertThat(moved.get(index).x() - moved.get(index - 1).x())
                    .describedAs("horizontal gap %s after moving by %s", index, wantedX)
                    .isEqualTo(group.get(index).x() - group.get(index - 1).x());
            assertThat(moved.get(index).y() - moved.get(index - 1).y())
                    .describedAs("vertical gap %s after moving by %s", index, wantedY)
                    .isEqualTo(group.get(index).y() - group.get(index - 1).y());
        }
    }
}
