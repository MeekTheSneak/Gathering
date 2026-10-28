package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How tall a pile of cards stands on the table in the world. */
class PileThicknessTest {

    @Test
    @DisplayName("an empty pile has no height, and a single card has some")
    void emptyAndOne() {
        assertThat(PileThickness.of(0, 1.0)).isZero();
        assertThat(PileThickness.of(-3, 1.0)).isZero();
        assertThat(PileThickness.of(1, 1.0)).isPositive();
    }

    @Test
    @DisplayName("a pile grows with every card until the tallest, then stops")
    void growsThenStops() {
        double previous = 0;
        for (int cards = 1; cards <= PileThickness.TALLEST; cards++) {
            double height = PileThickness.of(cards, 1.0);
            assertThat(height).as("%d cards", cards).isGreaterThan(previous);
            previous = height;
        }
        assertThat(PileThickness.of(250, 1.0)).isEqualTo(PileThickness.of(PileThickness.TALLEST, 1.0));
    }

    @Test
    @DisplayName("a sixty-card deck stands close to a sleeved one's proportions: about two thirds of a card's width")
    void aDeckLooksLikeADeck() {
        double deck = PileThickness.of(60, 1.0);
        assertThat(deck).isBetween(0.55, 0.7);
        // A hundred cards never stands taller than a card is wide.
        assertThat(PileThickness.of(PileThickness.TALLEST, 1.0)).isLessThan(1.1);
        // And three times the cards is three times as tall, which is what a player reads a stack's size off.
        assertThat(PileThickness.of(12, 1.0)).isCloseTo(PileThickness.of(4, 1.0) * 3, within(1e-9));
    }

    @Test
    @DisplayName("measured against the card, so a pile keeps its shape at any table size")
    void scalesWithTheCard() {
        assertThat(PileThickness.of(40, 0.2)).isCloseTo(PileThickness.of(40, 1.0) * 0.2, within(1e-12));
    }

    @Test
    @DisplayName("the side is banded every few cards, and a thin pile is one band")
    void bands() {
        assertThat(PileThickness.bands(1)).isEqualTo(1);
        assertThat(PileThickness.bands(9)).isEqualTo(1);
        assertThat(PileThickness.bands(10)).isEqualTo(2);
        assertThat(PileThickness.bands(60)).isEqualTo(12);
        assertThat(PileThickness.bands(500)).isEqualTo(PileThickness.TALLEST / PileThickness.CARDS_PER_BAND);
    }

    @Test
    @DisplayName("every side of a pile faces out of it, so the world does not cull the near walls")
    void sidesFaceOutward() {
        for (int side = 0; side < PileThickness.SIDES; side++) {
            double[][] corners = cornersOf(side, 0.3, 0.4, 0.0, 0.1);
            double[] along = minus(corners[1], corners[0]);
            double[] up = minus(corners[2], corners[1]);
            // Counterclockwise seen from outside is a cross product pointing out.
            double[] facing = {
                    along[1] * up[2] - along[2] * up[1],
                    along[2] * up[0] - along[0] * up[2],
                    along[0] * up[1] - along[1] * up[0]};
            int[] normal = {PileThickness.normalX(side), PileThickness.normalZ(side)};
            assertThat(facing[0] * normal[0] + facing[2] * normal[1]).as("side %d", side).isPositive();
            assertThat(facing[1]).as("side %d is upright", side).isZero();
            // And it lies on the side it says: every corner out at that edge.
            for (double[] corner : corners) {
                assertThat(corner[0] * normal[0] + corner[2] * normal[1])
                        .isEqualTo(normal[0] != 0 ? 0.3 : 0.4, within(1e-9));
            }
        }
    }

    @Test
    @DisplayName("a pile's sides are drawn from numbers, and drawing them makes no garbage")
    void aPilesSidesMakeNoGarbage() {
        // What the table in the world asks for every side of every band of every pile at every table in sight, every
        // frame. It asked for the corners and the facing as arrays, and wrote the texture's corners out as one: thirteen
        // small arrays a side, about four thousand a frame at a four-seat Commander table, measured at 263 KB.
        com.sun.management.ThreadMXBean threads = allocationCounting();
        double sum = 0;
        for (int round = 0; round < 50_000; round++) {
            sum += oneSideFromNumbers(round % PileThickness.SIDES);
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int round = 0; round < 100_000; round++) {
            sum += oneSideFromNumbers(round % PileThickness.SIDES);
        }
        long made = threads.getCurrentThreadAllocatedBytes() - before;
        assertThat(sum).isNotZero();
        assertThat(made).as("bytes allocated drawing 100,000 sides").isLessThan(16 * 1024);
    }

    @Test
    @DisplayName("the numbers are the corners and the facing the sides were drawn from, in the same order")
    void theNumbersAreTheCorners() {
        // The table the corners were written out in before they were numbers, kept here as the reference: each
        // side from its first end to its other along the bottom, then back along the top. Half width 0.3, half
        // depth 0.4, from 0.05 up to 0.25.
        double[][][] written = {
                {{0.3, 0.05, -0.4}, {-0.3, 0.05, -0.4}, {-0.3, 0.25, -0.4}, {0.3, 0.25, -0.4}},
                {{-0.3, 0.05, 0.4}, {0.3, 0.05, 0.4}, {0.3, 0.25, 0.4}, {-0.3, 0.25, 0.4}},
                {{0.3, 0.05, 0.4}, {0.3, 0.05, -0.4}, {0.3, 0.25, -0.4}, {0.3, 0.25, 0.4}},
                {{-0.3, 0.05, -0.4}, {-0.3, 0.05, 0.4}, {-0.3, 0.25, 0.4}, {-0.3, 0.25, -0.4}}};
        int[][] facing = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        for (int side = 0; side < PileThickness.SIDES; side++) {
            assertThat(cornersOf(side, 0.3, 0.4, 0.05, 0.25)).as("side %d", side).isEqualTo(written[side]);
            assertThat(new int[] {PileThickness.normalX(side), PileThickness.normalZ(side)})
                    .as("side %d faces", side).isEqualTo(facing[side]);
        }
    }

    /** One side's corners as {x, y, z}, read off the numbers the renderer reads. */
    private static double[][] cornersOf(int side, double halfWidth, double halfDepth, double bottom, double top) {
        double[][] corners = new double[PileThickness.CORNERS][];
        for (int corner = 0; corner < PileThickness.CORNERS; corner++) {
            corners[corner] = new double[] {
                    PileThickness.cornerX(side, corner, halfWidth, halfDepth),
                    PileThickness.atTheTop(corner) ? top : bottom,
                    PileThickness.cornerZ(side, corner, halfWidth, halfDepth)};
        }
        return corners;
    }

    /** One side's corners and facing, the way the renderer reads them, added up so nothing is optimized away. */
    private static double oneSideFromNumbers(int side) {
        double sum = PileThickness.normalX(side) + PileThickness.normalZ(side);
        for (int corner = 0; corner < PileThickness.CORNERS; corner++) {
            sum += PileThickness.cornerX(side, corner, 0.3, 0.4)
                    + PileThickness.cornerZ(side, corner, 0.3, 0.4)
                    + (PileThickness.atTheTop(corner) ? 0.1 : 0.0);
        }
        return sum;
    }

    /** The JVM's count of what this thread allocates, or the test is skipped where it will not say. */
    static com.sun.management.ThreadMXBean allocationCounting() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean counting
                && counting.isThreadAllocatedMemorySupported() && counting.isThreadAllocatedMemoryEnabled(),
                "this JVM does not count what a thread allocates");
        return (com.sun.management.ThreadMXBean) bean;
    }

    private static double[] minus(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
}
