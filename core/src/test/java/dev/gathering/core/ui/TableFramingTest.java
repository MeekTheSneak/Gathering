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
        Rect framed = TableFraming.everything(surfaceOf(4), Rect.NONE);

        assertThat(four.blocks(framed.width()))
                .isCloseTo(one.widthInBlocks() * 4, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        // And depth is its own answer: a row is as deep as one table and the hands held at its edges, not as deep
        // as it is long.
        assertThat(four.blocks(framed.height()))
                .isCloseTo(one.blocks(TableFraming.everything(surfaceOf(1), Rect.NONE).height()),
                        org.assertj.core.data.Offset.offset(CLOSE_ENOUGH))
                .isLessThan(four.blocks(framed.width()));
    }

    private static TableSurface surfaceOf(int tables) {
        return TableSurface.forSeatCount(tables * dev.gathering.core.table.TableCluster.SEATS_PER_TABLE);
    }

    /**
     * "Show everything" on the block takes in the pot, which lies off the east edge of the table, as the flat
     * board's does. It framed the table and every hand and left the pot beside the window, on the key a player
     * reaches for to find what is at stake.
     */
    @Test
    @DisplayName("show everything on the block frames the pot beside the table")
    void everythingTakesInThePot() {
        for (int tables = 1; tables <= 4; tables++) {
            TableSurface surface = surfaceOf(tables);
            for (int staked = 1; staked <= 12; staked++) {
                Rect tray = TableSurface.potTray(surface.pot(staked));
                Rect framed = TableFraming.everything(surface, tray);
                assertThat(contains(framed, tray)).as("%s tables, %s staked: %s frames the pot %s", tables, staked,
                        framed, tray).isTrue();
                assertThat(contains(framed, new Rect(0, 0, surface.width(), surface.height())))
                        .as("%s tables, %s staked: the table", tables, staked).isTrue();
                assertThat(contains(framed, surface.handReach()))
                        .as("%s tables, %s staked: every hand", tables, staked).isTrue();
            }
        }
    }

    /**
     * With nothing beside the table, the framing is the one the block always had: centered on the cluster, as wide
     * as it is, and as deep as it is with as much again both ways as the furthest hand reaches past an edge.
     */
    @Test
    @DisplayName("with no pot, show everything on the block frames what it always did")
    void everythingWithNoPotIsUnchanged() {
        for (int tables = 1; tables <= 4; tables++) {
            TableTop top = cluster(tables);
            TableSurface surface = surfaceOf(tables);
            Rect framed = TableFraming.everything(surface, Rect.NONE);
            Rect reach = surface.handReach();
            int past = Math.max(Math.max(0, -reach.y()), Math.max(0, reach.bottom() - surface.height()));
            assertThat(past).as("%s tables: a hand is held past the edge", tables).isPositive();
            assertThat(TableFraming.focusAcross(top, framed.centerX()))
                    .as("%s tables", tables).isCloseTo(0, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
            assertThat(TableFraming.focusDown(top, framed.centerY()))
                    .as("%s tables", tables).isCloseTo(0, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
            assertThat(top.blocks(framed.height())).as("%s tables", tables).isCloseTo(
                    top.depthInBlocks() + 2 * top.blocks(past), org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
            assertThat(top.blocks(framed.width())).as("%s tables", tables).isCloseTo(
                    top.widthInBlocks(), org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        }
    }

    private static boolean contains(Rect outer, Rect inner) {
        return inner.isEmpty() || (inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom());
    }

    @Test
    @DisplayName("one table answers exactly as it always did")
    void asingleTableIsUnchanged() {
        TableTop one = cluster(1);

        assertThat(one.blocks(TableFraming.everything(surfaceOf(1), Rect.NONE).width())).isCloseTo(
                TableTop.SPAN_BLOCKS, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
        assertThat(TableFraming.panReachAcross(one)).isCloseTo(
                TableTop.SPAN_BLOCKS / 2, org.assertj.core.data.Offset.offset(CLOSE_ENOUGH));
    }
}
