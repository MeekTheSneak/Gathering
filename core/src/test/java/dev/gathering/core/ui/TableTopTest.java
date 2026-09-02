package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pointing at a table that is in the world rather than on the screen.
 * <p>Playing on the block means the cursor is a ray and the table is a plane, and everything
 * a player does - picking a card up, seeing which one is under the pointer, letting one go -
 * rests on where those two meet. The failure to be afraid of is not a wrong answer but a
 * confident one: a ray aimed at the sky, or at a table behind the player, that comes back with
 * a point on the felt anyway. A card would then be dropped somewhere nobody was pointing.
 */
class TableTopTest {

    /** A table at the origin, two blocks across, at about waist height. */
    private static final TableTop TABLE = new TableTop(10.0, 15.94, 20.0, 1.76);

    @Nested
    @DisplayName("hitting the surface")
    class Hitting {

        @Test
        @DisplayName("looking straight down at the middle finds the middle")
        void straightDownFindsThePointBelow() {
            double middleX = TABLE.worldX(TableSurface.SPAN / 2.0);
            double middleZ = TABLE.worldZ(TableSurface.SPAN / 2.0);

            Optional<TableTop.Spot> spot = TABLE.hit(middleX, TABLE.topY() + 3, middleZ, 0, -1, 0);

            assertThat(spot).isPresent();
            assertThat(spot.get().x()).isCloseTo(TableSurface.SPAN / 2.0, within(0.001));
            assertThat(spot.get().y()).isCloseTo(TableSurface.SPAN / 2.0, within(0.001));
        }

        @Test
        @DisplayName("a ray from an angle lands where it points, not below where it started")
        void anAngledRayTravels() {
            // The one that makes a ray a ray. Somebody standing at the south edge looking down
            // at the far side of the table is pointing at the far side, and a cast that
            // ignored the horizontal part of the look vector would hand them the near edge.
            double startX = TABLE.worldX(TableSurface.SPAN / 2.0);
            double startZ = TABLE.worldZ(0);
            double height = 1.5;

            // Down and forward at forty-five degrees: it travels exactly its own height.
            Optional<TableTop.Spot> spot = TABLE.hit(
                    startX, TABLE.topY() + height, startZ, 0, -1, 1);

            assertThat(spot).isPresent();
            assertThat(spot.get().y())
                    .describedAs("traveled forward as far as it fell")
                    .isCloseTo(TableSurface.SPAN * (height / TABLE.span()), within(0.5));
        }

        @Test
        @DisplayName("looking up, level, or away from the table hits nothing")
        void raysThatCannotReachItMiss() {
            double x = TABLE.worldX(TableSurface.SPAN / 2.0);
            double z = TABLE.worldZ(TableSurface.SPAN / 2.0);
            double above = TABLE.topY() + 2;

            assertThat(TABLE.hit(x, above, z, 0, 1, 0)).describedAs("up").isEmpty();
            assertThat(TABLE.hit(x, above, z, 1, 0, 0)).describedAs("level").isEmpty();
            assertThat(TABLE.hit(x, TABLE.topY() - 2, z, 0, -1, 0))
                    .describedAs("below it, looking down").isEmpty();
            assertThat(TABLE.hit(x, above, z, 0, Double.NaN, 0)).describedAs("nowhere").isEmpty();
        }

        @Test
        @DisplayName("a ray that clears the table entirely is a miss, not the nearest edge")
        void pointingPastTheTableMisses() {
            // A card let go over the floor has to go back where it came from. Clamping to the
            // edge here would slide it onto somebody's board instead, which is a move nobody
            // made.
            double x = TABLE.worldX(TableSurface.SPAN / 2.0);
            double z = TABLE.worldZ(TableSurface.SPAN / 2.0);

            assertThat(TABLE.hit(x, TABLE.topY() + 1, z, 0, -1, 40)).isEmpty();
            assertThat(TABLE.at(TABLE.westX() - 0.01, TABLE.northZ() + 0.5)).isEmpty();
            assertThat(TABLE.at(TABLE.westX() + TABLE.span() + 0.01, TABLE.northZ() + 0.5))
                    .isEmpty();
        }

        @Test
        @DisplayName("the corners are on the table")
        void bothCornersCount() {
            assertThat(TABLE.at(TABLE.westX(), TABLE.northZ())).isPresent();
            assertThat(TABLE.at(TABLE.westX() + TABLE.span(), TABLE.northZ() + TABLE.span()))
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("surface and world agree")
    class TheRoundTrip {

        @Property(tries = 4000)
        void aPointGoesToTheWorldAndComesBack(
                @ForAll @DoubleRange(min = 0, max = TableSurface.SPAN) double across,
                @ForAll @DoubleRange(min = 0, max = TableSurface.SPAN) double down) {
            // Cards are drawn by going one way and picked by going the other, exactly as on
            // the screen, so the two directions have to be inverses here too.
            Optional<TableTop.Spot> back =
                    TABLE.at(TABLE.worldX(across), TABLE.worldZ(down));

            assertThat(back).isPresent();
            assertThat(back.get().x()).isCloseTo(across, within(0.001));
            assertThat(back.get().y()).isCloseTo(down, within(0.001));
        }

        @Property(tries = 3000)
        void lookingStraightDownAtAPointFindsThatPoint(
                @ForAll @DoubleRange(min = 0, max = TableSurface.SPAN) double across,
                @ForAll @DoubleRange(min = 0, max = TableSurface.SPAN) double down,
                @ForAll @DoubleRange(min = 0.1, max = 30) double height) {
            Optional<TableTop.Spot> spot = TABLE.hit(
                    TABLE.worldX(across), TABLE.topY() + height, TABLE.worldZ(down), 0, -1, 0);

            assertThat(spot).isPresent();
            assertThat(spot.get().x()).isCloseTo(across, within(0.01));
            assertThat(spot.get().y()).isCloseTo(down, within(0.01));
        }
    }

    @Test
    @DisplayName("a distance on the surface is a distance in blocks")
    void surfaceDistancesBecomeBlocks() {
        assertThat(TABLE.blocks(TableSurface.SPAN)).isCloseTo(TABLE.span(), within(0.000_001));
        assertThat(TABLE.blocks(0)).isZero();
    }

    @Test
    @DisplayName("a table with no surface is refused rather than dividing by nothing")
    void aTableNeedsASurface() {
        assertThatThrownBy(() -> new TableTop(0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
