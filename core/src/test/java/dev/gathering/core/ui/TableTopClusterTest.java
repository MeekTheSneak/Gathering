package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A cluster's whole surface can be pointed at, not just its first table.
 * <p>The shared surface grows by a table's worth for every table in the row, and the world
 * hit test was written for one table: it checked a point against one table's blocks and
 * clamped anything past them back inside. On a four-seat pod that is the second table's mats
 * drawn where nothing can be clicked, and an audit reproduced it by converting the far mat's
 * own centre into world coordinates and finding it unhittable.
 */
class TableTopClusterTest {

    @Test
    @DisplayName("the far table's mat can be hit through the surface it is drawn on")
    void thefarMatIsReachable() {
        TableSurface surface = TableSurface.forSeatCount(4);
        Rect farMat = surface.matOf(2);
        TableTop top = TableTop.forCluster(0, 0, 0, 2, 1);

        var spot = top.at(top.worldX(farMat.centerX()), top.worldZ(farMat.centerY()));

        assertThat(spot).describedAs("the middle of the second table's mat").isPresent();
        assertThat(spot.get().x()).isCloseTo(farMat.centerX(), org.assertj.core.data.Offset.offset(1.0));
        assertThat(spot.get().y()).isCloseTo(farMat.centerY(), org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("a cluster is as many tables wide as it has")
    void theSurfaceIsAsWideAsTheCluster() {
        TableTop one = TableTop.forCorner(0, 0, 0);
        TableTop four = TableTop.forCluster(0, 0, 0, 4, 1);

        assertThat(one.surfaceWidth()).isEqualTo(TableSurface.SPAN);
        assertThat(four.surfaceWidth()).isEqualTo(TableSurface.SPAN * 4.0);
        assertThat(four.widthInBlocks()).isEqualTo(one.widthInBlocks() * 4);
        assertThat(four.depthInBlocks()).isEqualTo(one.depthInBlocks());
    }

    @Test
    @DisplayName("a point past the end of the row is still off the table")
    void pastTheEndIsStillOff() {
        TableTop two = TableTop.forCluster(0, 0, 0, 2, 1);

        assertThat(two.at(two.widthInBlocks() + 0.5, 0.5)).isEmpty();
        assertThat(two.at(0.5, two.depthInBlocks() + 0.5)).isEmpty();
        assertThat(two.at(-0.5, 0.5)).isEmpty();
    }
}
