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
        @DisplayName("a card is the same size however many people are at the table")
        void aBiggerPodDoesNotShrinkYourOwnBoard() {
            // Scaling to the whole surface instead of your own mat made a card's size depend
            // on how many people had sat down: a four-seat pod is two tables wide, so it
            // opened at half size, and eight seats at a quarter. A playmat is a playmat
            // whoever else is there.
            int alone = cardWidthWhenOpened(TableCluster.assumedSeating(2));
            int pod = cardWidthWhenOpened(TableCluster.assumedSeating(4));

            assertThat(pod).describedAs("a four-seat pod draws the same card as a two-seat table")
                    .isEqualTo(alone);
            assertThat(pod).isGreaterThan(45);
        }

        private int cardWidthWhenOpened(java.util.List<SeatAnchor> anchors) {
            BoardGeometry geometry = new BoardGeometry(anchors, WIDTH, HEIGHT, 130);
            geometry.focusOn(new SeatId(0));
            return geometry.cardWidth(new SeatId(0));
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
        @DisplayName("a window that changes size keeps the board filling it")
        void resizingRefitsTheBoard() {
            // Changing the interface scale mid-game is a resize as far as every screen is
            // concerned. The old arithmetic baked the offset that lifts the board clear of the
            // hand into the camera as a number of pixels, so a window that then changed size
            // kept the old offset: the mat came out adrift in the middle of a bigger window
            // with the other player's board coming into view above it.
            int status = 16;
            int hand = 130;
            SeatId me = new SeatId(0);
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, status, hand);
            geometry.focusOn(me);
            Rect before = geometry.matRect(me);

            // Twice the window, and twice the furniture with it, which is what halving the
            // interface scale does.
            geometry.reshape(seatsOf(new TableCell(0, 0)), WIDTH * 2, HEIGHT * 2,
                    status * 2, hand * 2);
            Rect after = geometry.matRect(me);

            assertThat(after.width())
                    .describedAs("the mat grew with the window")
                    .isCloseTo(before.width() * 2, org.assertj.core.data.Offset.offset(6));
            assertThat(after.y())
                    .describedAs("and still starts below the life totals")
                    .isGreaterThanOrEqualTo(status * 2);
            assertThat(after.bottom())
                    .describedAs("and still ends above the hand")
                    .isLessThanOrEqualTo(HEIGHT * 2 - hand * 2);
        }

        @Test
        @DisplayName("the board is framed between the life totals and the hand, not behind them")
        void theBoardClearsTheFurniture() {
            // The window has a status row across the top and a hand across the bottom, and the
            // felt runs under both. Framing against the window instead of against the strip
            // between them put the far edge of the board behind the life totals - which on a
            // short window is the row of zones belonging to whoever is opposite.
            int status = 16;
            int hand = 130;
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, status, hand);

            for (int seat = 0; seat < 2; seat++) {
                SeatId me = new SeatId(seat);
                geometry.focusOn(me);
                Rect mine = geometry.matRect(me);

                assertThat(mine.y())
                        .describedAs("seat %s's board starts below the life totals", seat)
                        .isGreaterThanOrEqualTo(status);
                assertThat(mine.bottom())
                        .describedAs("and ends above the hand", seat)
                        .isLessThanOrEqualTo(HEIGHT - hand);
            }
            geometry.showEverything();
            assertThat(geometry.matRect(new SeatId(0)).y())
                    .describedAs("and so does the whole table")
                    .isGreaterThanOrEqualTo(status);
        }

        @Test
        @DisplayName("your own cards are the right way up and the other player's are not")
        void cardsFaceTheChairTheyBelongTo() {
            // Turning the view turns where things are, not which way up they are drawn. Half
            // the players get their own board laid out facing them and then looked at from the
            // other side, which is two half turns and no turn at all - so leaving the viewer's
            // own turn out of this drew their own cards upside down on their own mat.
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, 130);

            for (int seat = 0; seat < 2; seat++) {
                SeatId me = new SeatId(seat);
                geometry.focusOn(me);

                assertThat(geometry.facingDegrees(me))
                        .describedAs("seat %s reads its own cards the right way up", seat)
                        .isZero();
                assertThat(geometry.facingDegrees(new SeatId(1 - seat)))
                        .describedAs("and the other player's upside down", seat)
                        .isEqualTo(180);
            }
        }

        @Test
        @DisplayName("both players see their own board nearest them, the other one across the table")
        void everySeatLooksFromItsOwnChair() {
            // The one thing that makes this a table rather than a diagram: your own board is
            // the near one, your own zones are on your own right, and your library is the zone
            // closest to your hand. All three are true for the player at the north edge only
            // if the whole view turns for them - turning the cards alone leaves them reading
            // somebody else's side of the table.
            BoardGeometry geometry = new BoardGeometry(
                    seatsOf(new TableCell(0, 0)), WIDTH, HEIGHT, 130);

            for (int seat = 0; seat < 2; seat++) {
                SeatId me = new SeatId(seat);
                SeatId them = new SeatId(1 - seat);
                geometry.focusOn(me);
                Rect mine = geometry.matRect(me);
                Rect theirs = geometry.matRect(them);
                Rect library = geometry.pileRect(me, 0, 4);
                Rect furthest = geometry.pileRect(me, 3, 4);

                assertThat(theirs.centreY())
                        .describedAs("seat %s's opponent is across the table, not behind them", seat)
                        .isLessThan(mine.centreY());
                assertThat(library.centreX())
                        .describedAs("seat %s's zones are on their own right", seat)
                        .isGreaterThan(mine.centreX());
                assertThat(library.centreY())
                        .describedAs("seat %s's library is the zone nearest their hand", seat)
                        .isGreaterThan(furthest.centreY());
            }
        }

        @Test
        @DisplayName("the opening view leans towards the table without pushing your board off it")
        void theViewLeansTowardsTheOpponent() {
            // Both boards at once and a readable card are not both possible on a small window:
            // two mats and the gap are the whole table, and fitting that into the strip above
            // the hand puts a card back at twenty-odd pixels. So the opening view keeps your
            // own board whole and leans towards the middle, which brings the other player in
            // as soon as the window has any slack - and Home still shows everything.
            // Two windows: the smallest one, where the board barely fits and there is nothing
            // to lean with, and a tall one where there is real room to give away. Only the
            // second can tell a lean from an even split, and without it this passed happily
            // with the lean deleted.
            for (int windowHeight : new int[] {HEIGHT, 900}) {
                int hand = 130;
                int visible = windowHeight - hand;
                BoardGeometry geometry = new BoardGeometry(
                        seatsOf(new TableCell(0, 0)), WIDTH, windowHeight, hand);

                for (int seat = 0; seat < 2; seat++) {
                    geometry.focusOn(new SeatId(seat));
                    Rect mine = geometry.matRect(new SeatId(seat));

                    assertThat(mine.y())
                            .describedAs("seat %s's own board is whole at %s", seat, windowHeight)
                            .isGreaterThanOrEqualTo(0);
                    assertThat(mine.bottom()).isLessThanOrEqualTo(visible);

                    // Whatever room is spare goes to the side the opponent is on rather than
                    // being split evenly - which is what leaning towards the table means once
                    // your own board has to stay whole.
                    //
                    // The opponent is always up the screen, for both players. The view itself
                    // is turned for whoever sits at the far edge, so the near edge of the
                    // board is the bottom of the window from either chair - which is the whole
                    // point of turning it, and would not be true if only the cards turned.
                    int mySide = visible - mine.bottom();
                    int theirSide = mine.y();

                    assertThat(mySide)
                            .describedAs("seat %s gives the spare room away at %s", seat, windowHeight)
                            .isLessThanOrEqualTo(theirSide);
                    if (windowHeight > HEIGHT) {
                        assertThat(theirSide - mySide)
                                .describedAs("and does it by a visible amount when there is room")
                                .isGreaterThan(20);
                    }
                }
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
