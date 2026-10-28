package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where the board on the block puts the pot when something stands beside the table: lifted onto it, never past the
 * most, and not at all for what is lower than the felt, overhangs it with air under it, holds nothing up, or is not
 * under the tray.
 */
class PotLiftTest {

    /** A tray inside the block at x 0, z 0, as the pot's is inside the column of blocks beside a table. */
    private static final PotLift.Footprint TRAY = new PotLift.Footprint(0.3, 0.1, 0.5, 0.9);

    /** A full block in the column at x 0, z 0, in this layer counted from the felt's own. */
    private static PotLift.Box block(int layer) {
        return new PotLift.Box(0, 0, 1, 1, layer, layer + 1.0);
    }

    /** Something in that column spanning these heights, across the whole block. */
    private static PotLift.Box span(double bottom, double top) {
        return new PotLift.Box(0, 0, 1, 1, bottom, top);
    }

    private static double lift(PotLift.Box... standing) {
        return PotLift.above(TRAY, List.of(standing));
    }

    @Test
    @DisplayName("open air beside the table leaves the pot on the felt's own plane")
    void nothingThereIsNoLift() {
        assertThat(PotLift.above(TRAY, List.of())).isZero();
    }

    @Test
    @DisplayName("ground as high as the table's own block lifts the pot a sixteenth, onto it")
    void raisedGroundLiftsItOntoTheGround() {
        assertThat(lift(block(0))).isCloseTo(1.0 / 16.0, within(1e-9));
    }

    @Test
    @DisplayName("a wall two blocks high lifts the pot onto its top")
    void aWallTwoHighLiftsItOntoTheWall() {
        assertThat(lift(block(0), block(1))).isCloseTo(2 - PotLift.FELT, within(1e-9));
    }

    @Test
    @DisplayName("an overhang with air under it - a shelf, the ceiling of a low room - lifts nothing")
    void anOverhangIsNotStoodOn() {
        // Counted, the ceiling of a room two blocks high lifted the pot as far as it goes, which is inside the ceiling.
        assertThat(lift(block(1))).isZero();
        assertThat(lift(block(2))).isZero();
        assertThat(lift(span(2.0, 2.5))).isZero();
    }

    @Test
    @DisplayName("ground with a ceiling over it lifts the pot onto the ground and no further")
    void groundUnderACeiling() {
        assertThat(lift(block(0), block(2))).isCloseTo(1.0 / 16.0, within(1e-9));
    }

    @Test
    @DisplayName("a top slab, which the felt's height is inside, lifts the pot onto it")
    void aTopSlabAtTheFelt() {
        assertThat(lift(span(0.5, 1.0))).isCloseTo(1.0 / 16.0, within(1e-9));
    }

    @Test
    @DisplayName("a chest on a block lifts the pot onto the chest")
    void aChestOnABlock() {
        assertThat(lift(block(0), span(1.0, 1.875))).isCloseTo(1.875 - PotLift.FELT, within(1e-9));
    }

    @Test
    @DisplayName("a fence's post, taller than its block, lifts the pot onto the post when it is under the tray")
    void aFencePostUnderTheTray() {
        assertThat(lift(new PotLift.Box(0.375, 0.375, 0.625, 0.625, 0, 1.5)))
                .isCloseTo(1.5 - PotLift.FELT, within(1e-9));
    }

    @Test
    @DisplayName("a fence's post at the far side of a block the tray only reaches the edge of holds nothing up")
    void aFencePostNotUnderTheTray() {
        // The tray reaches a twentieth of a block into the column at x 1; the post stands in the middle of it.
        PotLift.Footprint edge = new PotLift.Footprint(0.75, 0.1, 1.05, 0.9);
        assertThat(PotLift.above(edge, List.of(new PotLift.Box(1.375, 0.375, 1.625, 0.625, 0, 1.5)))).isZero();
        // And a block beside the tray, sharing only its edge, holds nothing up either.
        assertThat(PotLift.above(edge, List.of(new PotLift.Box(1.05, 0, 2.05, 1, 0, 1)))).isZero();
    }

    @Test
    @DisplayName("the pot is one tray: lifted onto a wall, it goes onto leaves over the next block at that height too")
    void oneTrayOverTwoBlocks() {
        // A wall in the block at x 0, and air at the felt's layer in the block at x 1 with leaves over it. Lifted onto
        // the wall the pot would be inside the leaves where it lies over the next block, so it goes onto them.
        PotLift.Footprint across = new PotLift.Footprint(0.5, 0.1, 1.5, 0.9);
        assertThat(PotLift.above(across, List.of(block(0), new PotLift.Box(1, 0, 2, 1, 1, 2))))
                .isCloseTo(2 - PotLift.FELT, within(1e-9));
        // With nothing under it but the leaves, the pot is not lifted onto them.
        assertThat(PotLift.above(across, List.of(new PotLift.Box(1, 0, 2, 1, 1, 2)))).isZero();
    }

    @Test
    @DisplayName("whatever is lower than the felt - a chest on the floor, a slab, a carpet - lifts nothing")
    void lowerThanTheFeltLiftsNothing() {
        assertThat(lift(span(0, 0.875), span(0, 0.5), span(0, 1.0 / 16.0))).isZero();
    }

    @Test
    @DisplayName("a wall taller than the most the pot is lifted stops it at the most")
    void aTallWallStopsItAtTheMost() {
        assertThat(lift(block(0), block(1), block(2))).isEqualTo(PotLift.MOST);
        assertThat(lift(block(0), block(1), block(2), block(3))).isEqualTo(PotLift.MOST);
    }

    @Test
    @DisplayName("a shape with no height holds nothing up")
    void nothingToStandOn() {
        assertThat(PotLift.above(TRAY, java.util.Arrays.asList(null, span(0.5, 0.5)))).isZero();
    }

    @Test
    @DisplayName("the layers asked about are the felt's own and every one the most reaches into")
    void theLayersAskedReachTheMost() {
        int layers = PotLift.layersToAsk();
        // The last layer asked about starts below the most, so a block there can lift the pot; the next one starts at
        // or past it, so nothing there could.
        assertThat((double) layers - 1).isLessThan(PotLift.FELT + PotLift.MOST);
        assertThat((double) layers).isGreaterThanOrEqualTo(PotLift.FELT + PotLift.MOST);
    }
}
