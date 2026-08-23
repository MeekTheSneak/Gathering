package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where a card is drawn and where a dropped card lands.
 *
 * <p>Two conversions stacked on each other - a position on a mat becomes a point on the table,
 * and a point on the table becomes a pixel - and the screen goes one way to draw and the other
 * way to work out what the cursor is on. When they disagree by even a little, every card lands
 * a nudge from where it was let go and every click misses the thing it was aimed at. That is
 * the failure this whole file exists to catch, and it is not a failure anybody would see by
 * reading either conversion on its own.
 */
class BoardGeometryTest {

    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;

    @Nested
    @DisplayName("drawing and dropping agree")
    class TheRoundTrip {

        @Property(tries = 3000)
        void aCardDroppedWhereItIsDrawnStaysPut(
                @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int across,
                @ForAll @IntRange(min = 0, max = TablePosition.SPAN) int down,
                @ForAll @IntRange(min = 0, max = 1) int seatIndex,
                @ForAll @IntRange(min = 0, max = 14) int zoomSteps) {
            BoardGeometry geometry = twoSeats();
            for (int step = 0; step < zoomSteps; step++) {
                geometry.zoom(1.18, WIDTH / 2.0, HEIGHT / 2.0);
            }
            SeatId seat = new SeatId(seatIndex);
            TablePosition position = TablePosition.of(across, down);

            Rect drawn = geometry.rectOf(seat, position);
            TablePosition back = geometry.positionOn(seat, drawn.x(), drawn.y());

            // The tolerance has to come from how many table units a pixel is worth at this
            // zoom, not from a fixed number: zoomed out, one pixel is a large slice of a mat.
            double unitsPerPixel = TableSurface.SPAN / (geometry.camera().scale() * TableSurface.SPAN)
                    * (TableSurface.SPAN / (double) geometry.surface().matOf(seatIndex).width());
            double tolerance = 1.0 + 2.0 * unitsPerPixel;
            assertThat((double) back.x())
                    .describedAs("across, at scale %s", geometry.camera().scale())
                    .satisfiesAnyOf(
                            value -> assertThat(value).isCloseTo(across, org.assertj.core.data.Offset.offset(tolerance)),
                            value -> assertThat(value).isIn(0.0, (double) TablePosition.SPAN));
            assertThat((double) back.y())
                    .describedAs("down, at scale %s", geometry.camera().scale())
                    .satisfiesAnyOf(
                            value -> assertThat(value).isCloseTo(down, org.assertj.core.data.Offset.offset(tolerance)),
                            value -> assertThat(value).isIn(0.0, (double) TablePosition.SPAN));
        }

        @Test
        @DisplayName("dragging the felt brings the table with it, on both axes")
        void panMovesTheTableWithTheHand() {
            // Grab the felt and pull down-right: the mats come down-right. Checking the card
            // and its mat stay a fixed distance apart does not catch this - a pan with an axis
            // flipped moves both of them together, just the wrong way - so the direction has
            // to be asserted against the screen itself.
            BoardGeometry geometry = twoSeats();
            Rect before = geometry.matRect(new SeatId(0));

            geometry.pan(40, 25);
            Rect after = geometry.matRect(new SeatId(0));

            assertThat(after.x()).describedAs("dragged right").isGreaterThan(before.x());
            assertThat(after.y()).describedAs("dragged down").isGreaterThan(before.y());
        }

        @Property(tries = 2000)
        void panningNeverMovesACardRelativeToItsMat(
                @ForAll @IntRange(min = -600, max = 600) int pixelsX,
                @ForAll @IntRange(min = -600, max = 600) int pixelsY) {
            // Sliding the view must slide the mat and the card by the same amount. If the two
            // used different arithmetic, panning would drift cards off their own boards.
            BoardGeometry geometry = twoSeats();
            SeatId seat = new SeatId(0);
            TablePosition position = TablePosition.of(4000, 6000);

            Rect cardBefore = geometry.rectOf(seat, position);
            Rect matBefore = geometry.matRect(seat);
            geometry.pan(pixelsX, pixelsY);
            Rect cardAfter = geometry.rectOf(seat, position);
            Rect matAfter = geometry.matRect(seat);

            assertThat(cardAfter.x() - matAfter.x()).isBetween(
                    cardBefore.x() - matBefore.x() - 1, cardBefore.x() - matBefore.x() + 1);
            assertThat(cardAfter.y() - matAfter.y()).isBetween(
                    cardBefore.y() - matBefore.y() - 1, cardBefore.y() - matBefore.y() + 1);
        }
    }

    @Nested
    @DisplayName("whose board is it")
    class Seating {

        @Test
        @DisplayName("a point on a mat reports that seat, and the felt between reports nobody")
        void seatAtFindsMatsAndNotTheGap() {
            // Dropping is "whoever's mat it landed on", so this answer is the one that decides
            // whether a stolen creature ends up on your side or nowhere at all.
            BoardGeometry geometry = twoSeats();

            for (int index = 0; index < 2; index++) {
                Rect mat = geometry.matRect(new SeatId(index));
                assertThat(geometry.seatAt(mat.centreX(), mat.centreY()))
                        .describedAs("middle of seat %s's mat", index)
                        .isEqualTo(new SeatId(index));
            }
            assertThat(geometry.seatAt(WIDTH / 2.0, HEIGHT / 2.0)).isNull();
        }

        @Test
        @DisplayName("a drop past the edge of a mat lands on the edge rather than throwing")
        void droppingOffTheMatClamps() {
            // The client crash this replaced: dragging a card a few pixels beyond the felt.
            BoardGeometry geometry = twoSeats();

            TablePosition off = geometry.positionOn(new SeatId(0), -50_000, -50_000);

            assertThat(off.x()).isBetween(0, TablePosition.SPAN);
            assertThat(off.y()).isBetween(0, TablePosition.SPAN);
        }
    }

    @Nested
    @DisplayName("card size")
    class CardSize {

        @Test
        @DisplayName("cards are card-shaped, and never shrink to nothing")
        void cardsStayCardShaped() {
            BoardGeometry geometry = twoSeats();
            for (int step = 0; step < 20; step++) {
                int width = geometry.cardWidth(new SeatId(0));
                int height = geometry.cardHeight(new SeatId(0));
                assertThat(width).isPositive();
                assertThat(height).isPositive();
                assertThat(width / (double) height)
                        .describedAs("aspect at step %s", step)
                        .isCloseTo(488.0 / 680.0, org.assertj.core.data.Offset.offset(0.15));
                geometry.zoom(0.7, WIDTH / 2.0, HEIGHT / 2.0);
            }
        }

        @Test
        @DisplayName("a crowded table draws smaller cards than an empty one")
        void moreSeatsMeansSmallerCards() {
            // A mat is a share of the table and a card is a share of a mat, so eight players
            // get smaller cards - the same thing that happens when eight people sit at a real
            // table. Getting this wrong overlaps every board with its neighbours.
            BoardGeometry two = twoSeats();
            BoardGeometry many = geometryFor(new TableCell(0, 0), new TableCell(1, 0));

            assertThat(many.cardWidth(new SeatId(0))).isLessThanOrEqualTo(two.cardWidth(new SeatId(0)));
        }
    }

    @Nested
    @DisplayName("someone sitting down")
    class Reshaping {

        @Test
        @DisplayName("a new player arriving does not yank the view out from under you")
        void reshapeKeepsTheCamera() {
            BoardGeometry geometry = twoSeats();
            geometry.zoom(1.18, 100, 100);
            geometry.pan(80, -40);
            TableCamera before = geometry.camera();

            geometry.reshape(seatsOf(new TableCell(0, 0), new TableCell(1, 0)), WIDTH, HEIGHT);

            assertThat(geometry.camera()).isEqualTo(before);
            assertThat(geometry.surface().seatCount()).isGreaterThan(2);
        }

        @Test
        @DisplayName("framing the table shows all of it again")
        void showEverythingFrames() {
            BoardGeometry geometry = twoSeats();
            geometry.zoom(1.18, 10, 10);
            geometry.zoom(1.18, 10, 10);
            geometry.pan(400, 400);

            geometry.showEverything();

            for (int index = 0; index < 2; index++) {
                Rect mat = geometry.matRect(new SeatId(index));
                assertThat(mat.x()).isGreaterThanOrEqualTo(0);
                assertThat(mat.y()).isGreaterThanOrEqualTo(0);
                assertThat(mat.right()).isLessThanOrEqualTo(WIDTH);
                assertThat(mat.bottom()).isLessThanOrEqualTo(HEIGHT);
            }
        }
    }

    @Nested
    @DisplayName("framing")
    class Framing {

        @Test
        @DisplayName("the whole table fits, and above the hand rather than behind it")
        void theBoardIsFramedClearOfTheHand() {
            // Fitting the board to the whole window puts its near edge - the player's own
            // mat, the one they use - underneath their own hand.
            int handHeight = 130;
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, handHeight);

            for (int seat = 0; seat < 2; seat++) {
                Rect mat = geometry.matRect(new SeatId(seat));
                assertThat(mat.y()).describedAs("seat %s is on screen", seat).isGreaterThanOrEqualTo(0);
                assertThat(mat.bottom())
                        .describedAs("seat %s clears the hand", seat)
                        .isLessThanOrEqualTo(HEIGHT - handHeight);
            }
        }

        @Test
        @DisplayName("the view a player opens on draws a card big enough to read")
        void openingOnYourOwnBoardIsPlayable() {
            // Fitting the whole table in sounds friendly and plays terribly: a two-seat table
            // squeezed into a short window drew a card twenty-seven pixels wide while the same
            // card in hand was ninety. A board you cannot read is a board you cannot play on.
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, 130);

            geometry.focusOn(new SeatId(0));

            assertThat(geometry.cardWidth(new SeatId(0)))
                    .describedAs("a card on your own board, in pixels")
                    .isGreaterThan(45);
        }

        @Test
        @DisplayName("opening on your own board puts your own board in front of you")
        void openingOnYourOwnBoardCentresIt() {
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, 130);

            for (int seat = 0; seat < 2; seat++) {
                geometry.focusOn(new SeatId(seat));
                Rect mat = geometry.matRect(new SeatId(seat));
                assertThat(mat.centreX())
                        .describedAs("seat %s is centred across", seat)
                        .isCloseTo(WIDTH / 2.0, org.assertj.core.data.Offset.offset(4.0));
                assertThat(mat.centreY())
                        .describedAs("seat %s is in the visible part, not behind the hand", seat)
                        .isBetween(0.0, (double) (HEIGHT - 130));
            }
        }

        @Test
        @DisplayName("a wider table is fitted by its own proportions, not squeezed into a square")
        void aRectangularSurfaceFillsTheWindow() {
            // Two tables pushed together are twice as wide as they are deep. Fitting that as a
            // square left the board in a box in the middle with the window empty either side.
            BoardGeometry wide = new BoardGeometry(TableCluster.assumedSeating(4), WIDTH, HEIGHT, 0);

            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (int seat = 0; seat < wide.surface().seatCount(); seat++) {
                Rect mat = wide.matRect(new SeatId(seat));
                left = Math.min(left, mat.x());
                right = Math.max(right, mat.right());
                assertThat(mat.x()).isGreaterThanOrEqualTo(0);
                assertThat(mat.right()).isLessThanOrEqualTo(WIDTH);
            }
            assertThat(right - left)
                    .describedAs("a wide table uses the width it has")
                    .isGreaterThan(WIDTH * 3 / 4);
        }
    }

    // ------------------------------------------------------------- fixtures

    private static BoardGeometry twoSeats() {
        return geometryFor(new TableCell(0, 0));
    }

    private static BoardGeometry geometryFor(TableCell... cells) {
        return new BoardGeometry(seatsOf(cells), WIDTH, HEIGHT);
    }

    private static List<SeatAnchor> seatsOf(TableCell... cells) {
        return TableCluster.of(Set.of(cells)).seats();
    }
}
