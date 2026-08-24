package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
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
        // And a playmat is a playmat: pushing a table on makes the surface bigger rather than
        // making everybody's mat smaller, which is what squashing the cluster into a fixed
        // square used to do.
        assertThat(small.matOf(0)).isEqualTo(bigger.matOf(0));
        assertThat(bigger.width()).isGreaterThan(small.width());
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
        // Two pixels of the table, at sixteen pixels to the block and two blocks across.
        int border = 2 * (TableSurface.SPAN / 32);

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
    @DisplayName("a zone is the shape of the cards in it, and never bigger than one")
    void pilesAreCardShaped() {
        // A zone is a stack of cards, so its slot has to be a card. These were a share of the
        // mat's width by a share of its depth, which on a two-player table drew every library
        // as a letterbox with a stretched card in it.
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        for (int index = 0; index < 4; index++) {
            Rect pile = surface.pileSlot(0, index, 4);
            assertThat(pile.width() / (double) pile.height())
                    .describedAs("zone %s is card-shaped", index)
                    .isCloseTo(488.0 / 680.0, within(0.02));
            assertThat(pile.width())
                    .describedAs("zone %s is no bigger than the cards that go in it", index)
                    .isLessThanOrEqualTo((int) Math.round(surface.cardWidthOn(0)) + 1);
        }
    }

    @Test
    @DisplayName("the zones sit in a column down one edge of the mat, clear of each other")
    void pilesLieInAColumnOnTheMat() {
        // Down the side rather than across the near edge: the near edge is the part of a mat
        // you reach over constantly, and four zones along it are four things to knock into
        // every time you play a land.
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        Rect mat = surface.matOf(0);

        Rect previous = null;
        for (int index = 0; index < 4; index++) {
            Rect pile = surface.pileSlot(0, index, 4);
            assertThat(pile.x()).describedAs("zone %s starts on the mat", index)
                    .isGreaterThanOrEqualTo(mat.x());
            assertThat(pile.right()).describedAs("zone %s ends on the mat", index)
                    .isLessThanOrEqualTo(mat.right());
            assertThat(pile.y()).isGreaterThanOrEqualTo(mat.y());
            assertThat(pile.bottom()).isLessThanOrEqualTo(mat.bottom());
            if (previous != null) {
                assertThat(pile.overlaps(previous))
                        .describedAs("zones %s and %s overlap", index - 1, index).isFalse();
                assertThat(pile.x())
                        .describedAs("the column stays in one line").isEqualTo(previous.x());
            }
            previous = pile;
        }
    }

    @Test
    @DisplayName("the first zone is the one nearest its own player, whichever way the board faces")
    void theLibraryIsNearestItsOwnPlayer() {
        // The column runs away from the player, so the library - the zone touched most - is
        // the one closest to hand. Which way that is down the mat depends on which chair the
        // board belongs to, so it cannot be stated as "the top".
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        for (int seat = 0; seat < 2; seat++) {
            Rect mat = surface.matOf(seat);
            double nearEdge = surface.isTurned(seat) ? mat.y() : mat.bottom();
            double first = surface.pileSlot(seat, 0, 4).centreY();
            double last = surface.pileSlot(seat, 3, 4).centreY();

            assertThat(Math.abs(first - nearEdge))
                    .describedAs("seat %s keeps its library to hand", seat)
                    .isLessThan(Math.abs(last - nearEdge));
        }
    }

    @Test
    @DisplayName("a mat is marked off into a row for lands nearest its own player")
    void aMatHasALandsRow() {
        // A mat with nothing on it is otherwise a rectangle, and a rectangle does not tell a
        // player where to put their first land - which is the question every game starts with.
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        int count = Zone.PILES.size();

        for (int seat = 0; seat < 2; seat++) {
            Rect mat = surface.matOf(seat);
            Rect line = surface.matDivider(seat, count);
            Rect column = surface.pileGroup(seat, 0, count - 1, count);

            assertThat(line.isEmpty()).describedAs("seat %s has a line", seat).isFalse();

            double nearEdge = surface.isTurned(seat) ? mat.y() : mat.bottom();
            double farEdge = surface.isTurned(seat) ? mat.bottom() : mat.y();
            assertThat(Math.abs(line.centreY() - nearEdge))
                    .describedAs("seat %s marks the row off nearest itself", seat)
                    .isLessThan(Math.abs(line.centreY() - farEdge));
            assertThat(line.centreY())
                    .describedAs("seat %s keeps the line on its own mat", seat)
                    .isBetween((double) mat.y(), (double) mat.bottom());
            assertThat(line.overlaps(column))
                    .describedAs("seat %s does not draw the line through its own zones", seat)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the command zone stands apart at the far end of the column")
    void theCommandZoneIsSetApart() {
        // How the tables people already play on are marked out, and it says the right thing:
        // three zones a hand is in and out of all game, and one it touches twice. The gap is
        // what makes them read as two groups rather than four in a row.
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        int count = Zone.PILES.size();

        for (int seat = 0; seat < 2; seat++) {
            int graveyard = Zone.PILES.indexOf(Zone.GRAVEYARD);
            int library = Zone.PILES.indexOf(Zone.LIBRARY);
            int exile = Zone.PILES.indexOf(Zone.EXILE);
            int command = Zone.PILES.indexOf(Zone.COMMAND);

            double toLibrary = gapBetween(surface, seat, graveyard, library, count);
            double toExile = gapBetween(surface, seat, library, exile, count);
            double toCommand = gapBetween(surface, seat, exile, command, count);

            assertThat(toCommand)
                    .describedAs("seat %s sets its command zone apart", seat)
                    .isGreaterThan(toLibrary * 2);
            assertThat(toLibrary)
                    .describedAs("seat %s spaces the other three evenly", seat)
                    .isCloseTo(toExile, within(2.0));

            // And the graveyard is the one nearest its own player, which is the end of the
            // column the command zone is furthest from.
            double nearEdge = surface.isTurned(seat)
                    ? surface.matOf(seat).y() : surface.matOf(seat).bottom();
            assertThat(Math.abs(surface.pileSlot(seat, graveyard, count).centreY() - nearEdge))
                    .describedAs("seat %s keeps its graveyard to hand", seat)
                    .isLessThan(Math.abs(surface.pileSlot(seat, command, count).centreY() - nearEdge));
        }
    }

    @Test
    @DisplayName("a format with no commanders draws three zones and no gap")
    void withoutACommandZoneTheColumnIsThree() {
        // An empty box labelled with a zone the format does not have is a question every
        // player asks once and nobody asks twice.
        TableSurface surface = surfaceFor(new TableCell(0, 0));
        int three = Zone.PILES_WITHOUT_A_COMMAND_ZONE;

        assertThat(surface.pileSlot(0, three, three))
                .describedAs("there is no fourth zone to draw")
                .isEqualTo(Rect.NONE);

        double first = gapBetween(surface, 0, 0, 1, three);
        double second = gapBetween(surface, 0, 1, 2, three);
        assertThat(first)
                .describedAs("and the three that are left are evenly spaced")
                .isCloseTo(second, within(2.0));
    }

    private static double gapBetween(TableSurface surface, int seat, int one, int other, int count) {
        Rect first = surface.pileSlot(seat, one, count);
        Rect second = surface.pileSlot(seat, other, count);
        return Math.abs(second.centreY() - first.centreY()) - first.height();
    }

    @Test
    @DisplayName("the zone column is on its own player's right hand, whichever chair that is")
    void theZonesAreOnTheirOwnPlayersRight() {
        // Two players facing each other reach for their own libraries in mirror image, so the
        // column is at the east edge of the surface for one of them and the west edge for the
        // other. Stating it against the table instead - "the outer edge" - put both columns on
        // the same side, which is one player's right hand and the other player's left.
        TableSurface surface = TableSurface.forSeats(TableCluster.assumedSeating(4));

        for (int seat = 0; seat < surface.seatCount(); seat++) {
            Rect mat = surface.matOf(seat);
            Rect zone = surface.pileSlot(seat, 0, 4);

            // A turned seat is the one at the north edge, looking south, whose right hand is
            // the table's west - so its column is on the low-x side of its own mat.
            assertThat(zone.centreX() < mat.centreX())
                    .describedAs("seat %s keeps its zones on its own right", seat)
                    .isEqualTo(surface.isTurned(seat));
        }
    }

    @Test
    @DisplayName("a zone's name is written on the felt beside its slot, never across it")
    void zoneNamesSitBesideTheirSlots() {
        // A slot is one card wide because that is what it holds, and no zone name fits across
        // a card at the size a whole board is drawn at. So the name is given felt of its own,
        // on the mat side of the column - clear of the slot, and still on the mat.
        for (int seats : new int[] {2, 4}) {
            TableSurface surface = TableSurface.forSeats(TableCluster.assumedSeating(seats));
            for (int seat = 0; seat < surface.seatCount(); seat++) {
                Rect mat = surface.matOf(seat);
                for (int index = 0; index < 4; index++) {
                    Rect slot = surface.pileSlot(seat, index, 4);
                    Rect named = surface.pileLabel(seat, index, 4);
                    if (named.isEmpty()) {
                        continue;
                    }
                    assertThat(named.right() <= slot.x() || named.x() >= slot.right())
                            .describedAs("%s seats: seat %s zone %s is not written over its slot",
                                    seats, seat, index)
                            .isTrue();
                    assertThat(named.width()).isGreaterThan(slot.width());
                    assertThat(mat.contains(named.x(), named.y())).isTrue();
                    assertThat(mat.contains(named.right(), named.bottom())).isTrue();
                    // Inward, so the writing runs across the table rather than off the edge.
                    assertThat(named.centreX() < slot.centreX())
                            .describedAs("%s seats: seat %s zone %s is named towards the mat",
                                    seats, seat, index)
                            .isEqualTo(!surface.isTurned(seat));
                }
            }
        }
    }

    @Test
    @DisplayName("every zone in a column is written in the same space as its neighbours")
    void zoneNamesShareOneColumnOfFelt() {
        // Whether there is room to write a name is a question about the column, not about one
        // zone in it. Asking it of each label's own height meant rounding a different y for
        // each: on a real board one zone in four came out a pixel shorter than its
        // neighbours, failed the "is this legible" test on its own, and was the only zone left
        // unnamed - which reads as that zone being special rather than as a rounding error.
        for (int seats : new int[] {2, 4}) {
            TableSurface surface = TableSurface.forSeats(TableCluster.assumedSeating(seats));
            for (int seat = 0; seat < surface.seatCount(); seat++) {
                Rect first = surface.pileLabel(seat, 0, 4);
                for (int index = 1; index < 4; index++) {
                    Rect named = surface.pileLabel(seat, index, 4);
                    assertThat(named.isEmpty())
                            .describedAs("%s seats: seat %s names zone %s if it names any",
                                    seats, seat, index)
                            .isEqualTo(first.isEmpty());
                    if (first.isEmpty()) {
                        continue;
                    }
                    assertThat(named.x()).isEqualTo(first.x());
                    assertThat(named.width()).isEqualTo(first.width());
                    assertThat(named.height()).isEqualTo(first.height());
                }
            }
        }
    }

    @Test
    @DisplayName("the lands-row line stops short of the zone names rather than crossing one out")
    void theLandsRowDoesNotStrikeThroughAZoneName() {
        for (int seats : new int[] {2, 4}) {
            TableSurface surface = TableSurface.forSeats(TableCluster.assumedSeating(seats));
            for (int seat = 0; seat < surface.seatCount(); seat++) {
                Rect line = surface.matDivider(seat, 4);
                if (line.isEmpty()) {
                    continue;
                }
                for (int index = 0; index < 4; index++) {
                    Rect named = surface.pileLabel(seat, index, 4);
                    if (!named.isEmpty()) {
                        assertThat(clearOf(line, named))
                                .describedAs("%s seats: seat %s line clears zone %s's name",
                                        seats, seat, index)
                                .isTrue();
                    }
                }
                for (int index = 0; index < TableVerb.count(); index++) {
                    Rect verb = surface.verbSlot(seat, index, TableVerb.count());
                    if (!verb.isEmpty()) {
                        assertThat(clearOf(line, verb))
                                .describedAs("%s seats: seat %s line clears verb button %s",
                                        seats, seat, index)
                                .isTrue();
                    }
                }
            }
        }
    }

    /** Whether these two do not overlap at all. */
    private static boolean clearOf(Rect one, Rect other) {
        return one.right() <= other.x() || one.x() >= other.right()
                || one.bottom() <= other.y() || one.y() >= other.bottom();
    }

    @Test
    @DisplayName("two players' boards face each other rather than both facing the same way")
    void boardsFaceTheirOwnPlayers() {
        // The same position on each mat has to mean "in front of me" for both of them. Laying
        // every board out as though its player sat at the bottom of the table put one player's
        // zones along the far edge of their own mat, which from their chair is somebody else's
        // board.
        TableSurface surface = surfaceFor(new TableCell(0, 0));

        // The bottom of a player's own board, in the coordinates their cards are stored in.
        for (int seat = 0; seat < 2; seat++) {
            Rect mat = surface.matOf(seat);
            double drawnAt = surface.surfaceY(seat, TableSurface.SPAN);
            double ownEdge = surface.isTurned(seat) ? mat.y() : mat.bottom();

            assertThat(drawnAt)
                    .describedAs("the near edge of seat %s's board is the edge it sits at", seat)
                    .isCloseTo(ownEdge, within(1.0));
        }
        assertThat(surface.isTurned(0))
                .describedAs("and the two of them do not face the same way")
                .isNotEqualTo(surface.isTurned(1));
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
            assertThat(mat.right()).isLessThanOrEqualTo(surface.width());
            assertThat(mat.bottom()).isLessThanOrEqualTo(surface.height());
            // A mat too small to hold a few cards is a mat nobody can play on.
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
