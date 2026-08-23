package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.gathering.core.game.TablePosition;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Playmats: where everybody's board goes on one shared surface.
 *
 * <p>A card keeps saying where it is on its own mat - that is state, it is in the log and in
 * undo, and a position whose meaning depended on how many tables were currently pushed
 * together would move when somebody built a table two blocks away. So the thing under test is
 * the conversion between "where on my board" and "where on the table", plus the one property
 * that makes a shared surface work at all: no two mats overlap.
 */
class TableSurfaceTest {

    @Test
    @DisplayName("two players at one table get the two halves of it")
    void oneTableSeatsTwoOppositeMats() {
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        assertThat(surface.seatCount()).isEqualTo(2);
        assertThat(surface.matOf(0).overlaps(surface.matOf(1))).isFalse();
        assertThat(surface.matOf(0).isEmpty()).isFalse();
        assertThat(surface.matOf(1).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("a mat sits on the edge its player is actually standing at")
    void matsGoWhereThePlayerIs() {
        // The seat on the north edge gets the north half. Getting this backwards puts your
        // board across the table from you, which reads as somebody else's.
        List<SeatAnchor> anchors = TableCluster.of(Set.of(new TableCell(0, 0))).seats();
        TableSurface surface = TableSurface.forSeats(anchors);

        for (int seat = 0; seat < anchors.size(); seat++) {
            Rect mat = surface.matOf(seat);
            boolean nearTheTop = mat.centreY() < TableSurface.SPAN / 2.0;
            switch (anchors.get(seat).side()) {
                case NORTH -> assertThat(nearTheTop)
                        .describedAs("north seat's mat is on the north half").isTrue();
                case SOUTH -> assertThat(nearTheTop)
                        .describedAs("south seat's mat is on the south half").isFalse();
                default -> {
                    boolean nearTheLeft = mat.centreX() < TableSurface.SPAN / 2.0;
                    assertThat(nearTheLeft)
                            .describedAs("%s seat's mat", anchors.get(seat).side())
                            .isEqualTo(anchors.get(seat).side() == dev.gathering.core.table.Side.WEST);
                }
            }
        }
    }

    @Test
    @DisplayName("a table with nobody at it has no mats and does not fall over")
    void anEmptyClusterIsFine() {
        TableSurface surface = TableSurface.forSeats(List.of());

        assertThat(surface.seatCount()).isZero();
        assertThat(surface.matOf(0)).isEqualTo(Rect.NONE);
        assertThat(surface.seatAt(500, 500)).isEqualTo(-1);
    }

    @Test
    @DisplayName("the felt between mats belongs to nobody")
    void theGapIsNotAMat() {
        // Mats are inset so two read as two. A click in the gap has to reach the table rather
        // than being quietly counted as the nearest player's board.
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        assertThat(surface.seatAt(TableSurface.SPAN / 2.0, TableSurface.SPAN / 2.0)).isEqualTo(-1);
    }

    @Test
    @DisplayName("a position on a mat survives the trip to the surface and back")
    void positionsRoundTrip() {
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        TablePosition position = TablePosition.of(3000, 7000);

        double x = surface.surfaceX(0, position.x());
        double y = surface.surfaceY(0, position.y());
        TablePosition back = surface.positionOn(0, x, y);

        assertThat((double) back.x()).isCloseTo(position.x(), within(2.0));
        assertThat((double) back.y()).isCloseTo(position.y(), within(2.0));
    }

    @Test
    @DisplayName("pushing another table on moves the mats but not what a card's position means")
    void aCardsPositionIsIndependentOfTheCluster() {
        // The whole reason positions stay per-mat. Building a table next door must not move
        // anybody's cards, and it does not, because a position never mentions the cluster.
        TableSurface small = surfaceFor(new TableCell(0, 0));
        TableSurface bigger = surfaceFor(new TableCell(0, 0), new TableCell(1, 0));
        TablePosition position = TablePosition.of(2500, 2500);

        assertThat(small.positionOn(0, small.surfaceX(0, position.x()), small.surfaceY(0, position.y())))
                .isEqualTo(bigger.positionOn(
                        0, bigger.surfaceX(0, position.x()), bigger.surfaceY(0, position.y())));
        assertThat(small.matOf(0)).isNotEqualTo(bigger.matOf(0));
    }

    @Test
    @DisplayName("a playmat is the two-by-one it sits in, less two pixels of border")
    void aMatIsTwoBlocksByOne() {
        // The surface is the table's whole top, two blocks across, so a pixel is a
        // thirty-second of the span. Two players get half the depth each, and the border takes
        // two pixels off every side of that.
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        // Two pixels, worked out the way the border is rather than as SPAN/32 doubled - a
        // thirty-second of ten thousand is not a whole number and the rounding is worth two
        // units either way.
        int border = TableSurface.SPAN * 2 / 32;

        for (int seat = 0; seat < 2; seat++) {
            Rect mat = surface.matOf(seat);
            assertThat(mat.width())
                    .describedAs("two blocks wide less two pixels each side")
                    .isEqualTo(TableSurface.SPAN - 2 * border);
            assertThat(mat.height())
                    .describedAs("one block deep less two pixels each side")
                    .isEqualTo(TableSurface.SPAN / 2 - 2 * border);
        }
    }

    @Test
    @DisplayName("a zone is the shape of the cards in it")
    void pilesAreCardShaped() {
        // A pile is a stack of cards, so its slot has to be a card. These were a share of the
        // mat's width by a share of its depth, which on a two-player table drew every library
        // as a letterbox with a stretched card in it.
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        for (int index = 0; index < 4; index++) {
            Rect pile = surface.pileSlot(0, index, 4);
            assertThat(pile.width() / (double) pile.height())
                    .describedAs("pile %s is card-shaped", index)
                    .isCloseTo(488.0 / 680.0, within(0.02));
            assertThat(pile.width()).isEqualTo((int) Math.round(surface.cardWidthOn(0)));
        }
    }

    @Test
    @DisplayName("the zones sit in a row on the mat, not on top of each other or off the edge")
    void pilesLieInARowOnTheMat() {
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        Rect mat = surface.matOf(0);

        Rect previous = null;
        for (int index = 0; index < 4; index++) {
            Rect pile = surface.pileSlot(0, index, 4);
            assertThat(pile.x()).describedAs("pile %s starts on the mat", index)
                    .isGreaterThanOrEqualTo(mat.x());
            assertThat(pile.right()).describedAs("pile %s ends on the mat", index)
                    .isLessThanOrEqualTo(mat.right());
            assertThat(pile.bottom()).isLessThanOrEqualTo(mat.bottom());
            assertThat(pile.y()).isGreaterThanOrEqualTo(mat.y());
            if (previous != null) {
                assertThat(pile.overlaps(previous))
                        .describedAs("piles %s and %s overlap", index - 1, index).isFalse();
                assertThat(pile.x()).isGreaterThan(previous.x());
            }
            previous = pile;
        }
    }

    @Test
    @DisplayName("a point on a zone finds that zone, and the rest of the mat finds none")
    void pilesCanBeDroppedInto() {
        // Dropping a card into the graveyard is aiming at it, so the answer to "which zone is
        // this point on" has to be the same one the drawing used.
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        for (int index = 0; index < 4; index++) {
            Rect pile = surface.pileSlot(0, index, 4);
            assertThat(surface.pileAt(0, 4, pile.centreX(), pile.centreY()))
                    .describedAs("the middle of pile %s", index)
                    .isEqualTo(index);
        }
        Rect mat = surface.matOf(0);
        assertThat(surface.pileAt(0, 4, mat.x() + 10, mat.y() + 10))
                .describedAs("the far corner of the mat is not a zone")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("cards are big enough to read on the mat they are on")
    void cardsAreABigEnoughShareOfTheMat() {
        // The size used to come off the mat's shorter side, which on a two-player table - full
        // width, half depth - made a card a twentieth of the mat's width and the board a
        // mosaic from directly above it.
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        Rect mat = surface.matOf(0);

        double across = mat.width() / surface.cardWidthOn(0);
        assertThat(across)
                .describedAs("cards across a playmat")
                .isBetween(6.0, 11.0);
        assertThat(surface.cardWidthOn(0) / surface.cardHeightOn(0))
                .isCloseTo(488.0 / 680.0, within(0.01));
    }

    @Property(tries = 500)
    void noTwoMatsEverOverlap(@ForAll("clusters") Set<TableCell> cells) {
        // The property that makes a shared surface a table rather than a pile. Two mats on top
        // of each other is two players whose boards cannot be told apart.
        TableSurface surface = TableSurface.forSeats(TableCluster.of(cells).seats());

        for (int first = 0; first < surface.seatCount(); first++) {
            for (int second = first + 1; second < surface.seatCount(); second++) {
                assertThat(surface.matOf(first).overlaps(surface.matOf(second)))
                        .describedAs("mats %s and %s overlap on %s", first, second, cells)
                        .isFalse();
            }
        }
    }

    @Property(tries = 500)
    void everyMatIsOnTheSurfaceAndBigEnoughToPlayOn(@ForAll("clusters") Set<TableCell> cells) {
        TableSurface surface = TableSurface.forSeats(TableCluster.of(cells).seats());

        for (int seat = 0; seat < surface.seatCount(); seat++) {
            Rect mat = surface.matOf(seat);
            assertThat(mat.x()).isGreaterThanOrEqualTo(0);
            assertThat(mat.y()).isGreaterThanOrEqualTo(0);
            assertThat(mat.right()).isLessThanOrEqualTo(TableSurface.SPAN);
            assertThat(mat.bottom()).isLessThanOrEqualTo(TableSurface.SPAN);
            // A mat too small to hold a few cards is a mat nobody can play on. An eighth of
            // the table each is the worst case, at eight seats.
            assertThat(mat.width()).isGreaterThan(TableSurface.SPAN / 20);
            assertThat(mat.height()).isGreaterThan(TableSurface.SPAN / 20);
        }
    }

    @Property(tries = 500)
    void everySeatCanFindItsOwnMatBack(@ForAll("clusters") Set<TableCell> cells) {
        TableSurface surface = TableSurface.forSeats(TableCluster.of(cells).seats());

        for (int seat = 0; seat < surface.seatCount(); seat++) {
            Rect mat = surface.matOf(seat);
            assertThat(surface.seatAt(mat.centreX(), mat.centreY()))
                    .describedAs("the middle of seat %s's mat", seat)
                    .isEqualTo(seat);
        }
    }

    @Property(tries = 2000)
    void aPositionAnywhereOnAMatStaysOnThatMat(
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
            @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down,
            @ForAll @IntRange(min = 0, max = 1) int seat) {
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        Rect mat = surface.matOf(seat);

        double x = surface.surfaceX(seat, across);
        double y = surface.surfaceY(seat, down);

        assertThat(x).isBetween((double) mat.x(), (double) mat.right());
        assertThat(y).isBetween((double) mat.y(), (double) mat.bottom());
    }

    // ------------------------------------------------------------- fixtures

    private static TableSurface surfaceFor(TableCell... cells) {
        return TableSurface.forSeats(TableCluster.of(Set.of(cells)).seats());
    }

    /**
     * Clusters somebody could actually build: up to four connected cells.
     *
     * <p>Grown by stepping rather than by picking coordinates, because a random set of cells
     * is usually not connected and the cluster arithmetic would discard most of it.
     */
    @Provide
    Arbitrary<Set<TableCell>> clusters() {
        return Combinators.combine(
                        Arbitraries.integers().between(1, TableCluster.MAX_TABLES),
                        Arbitraries.integers().between(0, 3).list().ofSize(6))
                .as(TableSurfaceTest::grow);
    }

    private static Set<TableCell> grow(int wanted, List<Integer> steps) {
        List<TableCell> cells = new ArrayList<>();
        cells.add(new TableCell(0, 0));
        Set<TableCell> present = new HashSet<>(cells);

        int step = 0;
        while (present.size() < wanted && step < steps.size()) {
            TableCell from = cells.get(step % cells.size());
            TableCell next = from.step(dev.gathering.core.table.Side.values()[steps.get(step) % 4]);
            if (present.add(next)) {
                cells.add(next);
            }
            step++;
        }
        return present;
    }
}
