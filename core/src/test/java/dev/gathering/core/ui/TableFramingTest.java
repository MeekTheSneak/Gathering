package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The camera's arithmetic over a cluster, checked without a window.
 * <p>An audit found the four answers disagreeing: the eye is placed at the middle of the whole
 * cluster, while the focus offset was measured from the middle of the first table, the pan
 * clamp used one table's span on both axes, and "show everything" framed one table squared.
 * All four are the same for a single table, which is why every test that existed passed.
 */
class TableFramingTest {

    private static final double CLOSE_ENOUGH = 1e-9;

    private static TableTop cluster(int tables) {
        return TableTop.forCluster(0, 64, 0, tables, 1);
    }

    @Test
    @DisplayName("the middle of the cluster is where the eye already sits, so it does not move")
    void themiddleIsNoOffsetAtAll() {
        for (int tables = 1; tables <= 4; tables++) {
            TableTop top = cluster(tables);

            assertThat(TableFraming.focusAcross(top, top.surfaceWidth() / 2.0))
                    .describedAs("%s tables", tables)
                    .isCloseTo(0, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
            assertThat(TableFraming.focusDown(top, top.surfaceDepth() / 2.0))
                    .isCloseTo(0, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        }
    }

    @Test
    @DisplayName("focusing a mat puts that mat under the eye, however many tables there are")
    void focusLandsOnTheThingItNamed() {
        for (int tables = 1; tables <= 4; tables++) {
            TableTop top = cluster(tables);
            // The middle of the last table's own surface, which is the far seat of a long row.
            double matX = TableSurface.SPAN * (tables - 0.5);

            double eyeX = top.worldX(top.surfaceWidth() / 2.0) + TableFraming.focusAcross(top, matX);

            assertThat(eyeX)
                    .describedAs("%s tables: the eye must land on the mat it was pointed at", tables)
                    .isCloseTo(top.worldX(matX), org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        }
    }

    @Test
    @DisplayName("the pan clamp reaches both ends of the cluster and no further")
    void panReachesTheWholeRow() {
        TableTop four = cluster(4);

        assertThat(TableFraming.panReachAcross(four))
                .isCloseTo(four.widthInBlocks() / 2, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        // Far enough to put the eye over the outermost mat, which one table's span was not.
        assertThat(TableFraming.panReachAcross(four))
                .isGreaterThanOrEqualTo(
                        Math.abs(TableFraming.focusAcross(four, TableSurface.SPAN * 3.5)));
        // And depth is its own answer: a row is not a square.
        assertThat(TableFraming.panReachDown(four))
                .isCloseTo(four.depthInBlocks() / 2, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        assertThat(TableFraming.panReachDown(four)).isLessThan(TableFraming.panReachAcross(four));
    }

    @Test
    @DisplayName("show everything frames the whole cluster, not one table of it")
    void everythingMeansEverything() {
        TableTop four = cluster(4);
        TableTop one = cluster(1);

        assertThat(TableFraming.everythingAcross(four))
                .isCloseTo(one.widthInBlocks() * 4, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        assertThat(TableFraming.everythingDown(four))
                .isCloseTo(one.depthInBlocks(), org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
    }

    @Test
    @DisplayName("one table answers exactly as it always did")
    void asingleTableIsUnchanged() {
        TableTop one = cluster(1);

        assertThat(TableFraming.everythingAcross(one)).isCloseTo(
                TableTop.SPAN_BLOCKS, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        assertThat(TableFraming.panReachAcross(one)).isCloseTo(
                TableTop.SPAN_BLOCKS / 2, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
    }
}
