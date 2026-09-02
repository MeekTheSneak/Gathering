package dev.gathering.core.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turning a two-by-two, which is what a building full of tables does when it is placed.
 * <p>The block side of this lives in {@code TablePart} and cannot be checked here, so what is
 * checked is the arithmetic it does: turning the four corners of a square a quarter turn is a
 * cycle of length four that visits every corner, and nothing else is a rotation.
 */
class TableCellTurningTest {

    /** The four corners, as directions away from the middle of a two-by-two. */
    private static final List<int[]> CORNERS =
            List.of(new int[] {-1, -1}, new int[] {1, -1}, new int[] {1, 1}, new int[] {-1, 1});

    @Test
    @DisplayName("a quarter turn sends every corner to a different corner")
    void aQuarterTurnIsAPermutation() {
        Set<String> landed = new LinkedHashSet<>();
        for (int[] corner : CORNERS) {
            int[] turned = {-corner[1], corner[0]};
            assertThat(CORNERS).anySatisfy(other ->
                    assertThat(other).containsExactly(turned[0], turned[1]));
            landed.add(turned[0] + "," + turned[1]);
        }
        assertThat(landed)
                .as("four corners in, four different corners out")
                .hasSize(4);
    }

    @Test
    @DisplayName("four quarter turns are none at all")
    void fourQuarterTurnsComeHome() {
        for (int[] corner : CORNERS) {
            int x = corner[0];
            int z = corner[1];
            for (int turn = 0; turn < 4; turn++) {
                int wasX = x;
                x = -z;
                z = wasX;
            }
            assertThat(new int[] {x, z}).containsExactly(corner[0], corner[1]);
        }
    }
}
