package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.table.TableCluster;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Each seat's hand, fanned at the near edge of its mat, where its player holds it.
 * <p>Both boards draw it from this, the flat one and the one on the block, so what is worth pinning
 * is that it is a fan somebody would recognize - a hand, turned toward its player - that it stays
 * on that player's strip of table and off everything anybody else owns, and that it is decided by
 * a count and nothing else.
 */
class FeltHandTest {

    /** The four corners of a card lying at its slot's angle, as x and y pairs. */
    private static double[][] cornersOf(FeltHand.Slot slot, int facing) {
        Rect card = slot.where();
        double turn = Math.toRadians(slot.angle() + facing);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double[][] corners = new double[4][];
        int at = 0;
        for (int across = -1; across <= 1; across += 2) {
            for (int down = -1; down <= 1; down += 2) {
                double x = across * card.width() / 2.0;
                double y = down * card.height() / 2.0;
                corners[at++] = new double[] {
                        card.centerX() + x * cos - y * sin, card.centerY() + x * sin + y * cos};
            }
        }
        return corners;
    }

    @Nested
    @net.jqwik.api.Group
    @DisplayName("where a hand lies")
    class WhereAHandLies {

        /**
         * Every corner of every card, turned the way it is drawn, on its own seat's strip of table.
         * <p>A card that pokes out of the strip is a card on the mat behind it or off the table in
         * front, and the fan's corners are what reach furthest - a test of the rectangles alone would
         * pass a fan whose ends hang over the mat.
         */
        @Property(tries = 400)
        @Label("every corner of every card lies in its own seat's strip")
        void everyCardLiesInItsBand(
                @ForAll @IntRange(min = 1, max = 8) int seats,
                @ForAll @IntRange(min = 0, max = 7) int seatIndex,
                @ForAll @IntRange(min = 0, max = 60) int cards) {
            TableSurface surface = TableSurface.forSeatCount(seats);
            int seat = seatIndex % surface.seatCount();
            Rect band = surface.handBand(seat);
            List<FeltHand.Slot> fan = surface.handFan(seat, cards);

            assertThat(band.isEmpty()).as("seat %s of %s has no strip", seat, seats).isFalse();
            for (FeltHand.Slot slot : fan) {
                for (double[] corner : cornersOf(slot, surface.facingDegrees(seat))) {
                    assertThat(corner[0]).as("a corner of %s in %s", slot, band)
                            .isBetween((double) band.x(), (double) band.right());
                    assertThat(corner[1]).as("a corner of %s in %s", slot, band)
                            .isBetween((double) band.y(), (double) band.bottom());
                }
            }
        }

        /**
         * A seat's strip covers nothing anybody owns: no mat, no life total, no counters, the pot,
         * or another seat's strip.
         * <p>The pot's own test used to restate how wide the old row of backs was drawn; this is the
         * strip itself, which every fan is inside by the property above.
         */
        @Property(tries = 200)
        @Label("a hand's strip covers nothing any seat owns, nor the pot")
        void aBandCoversNothingOwned(
                @ForAll @IntRange(min = 1, max = 8) int seats,
                @ForAll @IntRange(min = 1, max = 40) int staked) {
            TableSurface surface = TableSurface.forSeatCount(seats);
            Rect tray = TableSurface.potTray(surface.pot(staked));
            for (int seat = 0; seat < surface.seatCount(); seat++) {
                Rect band = surface.handBand(seat);
                assertThat(band.overlaps(tray)).as("seat %s's strip %s under the pot %s", seat, band, tray)
                        .isFalse();
                for (int other = 0; other < surface.seatCount(); other++) {
                    assertThat(band.overlaps(surface.matOf(other)))
                            .as("seat %s's strip %s over seat %s's mat", seat, band, other).isFalse();
                    assertThat(band.overlaps(surface.lifeBox(other)))
                            .as("seat %s's strip %s over seat %s's life", seat, band, other).isFalse();
                    assertThat(band.overlaps(surface.countersBox(other)))
                            .as("seat %s's strip %s over seat %s's counters", seat, band, other).isFalse();
                    if (other != seat) {
                        assertThat(band.overlaps(surface.handBand(other)))
                                .as("seats %s and %s share a strip", seat, other).isFalse();
                    }
                }
            }
        }

        /**
         * Held at the edge its player sits at: from the near edge of the mat, a card deep, so the
         * fan lies half on the felt and half over the table's edge, where a hand is held.
         * <p>Only the felt strip between the mat and the edge was tried first. It is a third of a
         * card deep, and the fan fitted into it drew a hand of seven as a smudge of the sleeve's
         * color at "show everything".
         */
        @Test
        @DisplayName("a hand is held at its player's edge of the table")
        void aHandIsHeldAtItsPlayersEdge() {
            for (int seats : new int[] {2, 4, 8}) {
                TableSurface surface = TableSurface.forSeatCount(seats);
                for (int seat = 0; seat < surface.seatCount(); seat++) {
                    Rect mat = surface.matOf(seat);
                    Rect band = surface.handBand(seat);
                    assertThat(band.x()).isEqualTo(mat.x());
                    assertThat(band.width()).isEqualTo(mat.width());
                    assertThat(band.height()).isEqualTo((int) Math.round(surface.cardHeightOn(seat)));
                    if (surface.isTurned(seat)) {
                        assertThat(band.bottom()).as("seat %s's hand against its mat", seat).isEqualTo(mat.y());
                        assertThat(band.y()).as("seat %s's hand past the table's edge", seat).isNegative();
                    } else {
                        assertThat(band.y()).as("seat %s's hand against its mat", seat).isEqualTo(mat.bottom());
                        assertThat(band.bottom()).as("seat %s's hand past the table's edge", seat)
                                .isGreaterThan(surface.height());
                    }
                }
            }
        }

        /**
         * Showing the whole table shows every hand on it.
         * <p>A hand is held partly past the table's edge, so framing the table alone put the far
         * player's under the strip along the top. Down to the smallest interface and the largest
         * cluster.
         */
        @Test
        @DisplayName("showing everything shows every hand")
        void showingEverythingShowsEveryHand() {
            int[][] windows = {{427, 240, 76, 16}, {320, 240, 76, 16}, {854, 480, 134, 16}};
            for (int[] window : windows) {
                for (int seats : new int[] {2, 4, 8}) {
                    BoardGeometry geometry = new BoardGeometry(TableCluster.assumedSeating(seats),
                            window[0], window[1], window[3], window[2]);
                    geometry.showEverything();
                    for (int seat = 0; seat < geometry.surface().seatCount(); seat++) {
                        Rect band = geometry.fromSurface(geometry.surface().handBand(seat));
                        assertThat(band.y()).as("seat %s of %s at %sx%s", seat, seats, window[0], window[1])
                                .isGreaterThanOrEqualTo(window[3]);
                        assertThat(band.bottom()).as("seat %s of %s at %sx%s", seat, seats, window[0], window[1])
                                .isLessThanOrEqualTo(window[1] - window[2]);
                        assertThat(band.x()).isGreaterThanOrEqualTo(0);
                        assertThat(band.right()).isLessThanOrEqualTo(window[0]);
                    }
                }
            }
        }

        /** A card drawn flies into the fan: to the card a hand of one is, inside the strip. */
        @Property(tries = 100)
        @Label("a card drawn lands in the fan")
        void aCardDrawnLandsInTheFan(
                @ForAll @IntRange(min = 1, max = 8) int seats,
                @ForAll @IntRange(min = 0, max = 7) int seatIndex) {
            TableSurface surface = TableSurface.forSeatCount(seats);
            int seat = seatIndex % surface.seatCount();
            Rect landing = surface.handEdge(seat);

            assertThat(landing).isEqualTo(surface.handFan(seat, 1).get(0).where());
            Rect band = surface.handBand(seat);
            assertThat(band.contains(landing.x(), landing.y())
                    && band.contains(landing.right() - 1, landing.bottom() - 1))
                    .as("landing %s outside the strip %s", landing, band).isTrue();
            // And the size of a card in the fan, not of one on the mat - it is joining the fan.
            FeltHand.Slot anyCard = surface.handFan(seat, 7).get(3);
            assertThat(landing.width()).isEqualTo(anyCard.where().width());
            assertThat(landing.height()).isEqualTo(anyCard.where().height());
        }
    }

    @Nested
    @net.jqwik.api.Group
    @DisplayName("what a hand looks like")
    class WhatAHandLooksLike {

        @Test
        @DisplayName("no cards, or no room, is no fan")
        void nothingIsNothing() {
            Rect band = new Rect(0, 0, 9000, 400);
            assertThat(FeltHand.of(band, false, 0, 600)).isEmpty();
            assertThat(FeltHand.of(band, false, -3, 600)).isEmpty();
            assertThat(FeltHand.of(Rect.NONE, false, 5, 600)).isEmpty();
            assertThat(FeltHand.of(band, false, 5, 0)).isEmpty();
            assertThat(FeltHand.landing(Rect.NONE, false, 600)).isEqualTo(Rect.NONE);
        }

        /**
         * Past ten the fan stays the fan of ten.
         * <p>Ten reads as "a lot" already, the number is in the strip along the top, and a fan of forty
         * backs is four hundred draws a frame saying the same thing as the fan of ten.
         */
        @Property(tries = 100)
        @Label("a big hand is shown as the most a fan shows")
        void aBigHandIsCapped(@ForAll @IntRange(min = 1, max = 120) int cards) {
            TableSurface surface = TableSurface.forSeatCount(2);
            List<FeltHand.Slot> fan = surface.handFan(1, cards);

            assertThat(fan).hasSize(Math.min(cards, FeltHand.MOST_SHOWN));
            if (cards > FeltHand.MOST_SHOWN) {
                assertThat(fan).isEqualTo(surface.handFan(1, FeltHand.MOST_SHOWN));
            }
        }

        /**
         * One card size however many cards: a hand whose cards shrank as it grew would read as a
         * hand moving away. And never bigger than a card on the mat behind it.
         */
        @Property(tries = 200)
        @Label("every card in a fan is one size, no bigger than the table's own")
        void everyCardIsOneSize(
                @ForAll @IntRange(min = 1, max = 8) int seats,
                @ForAll @IntRange(min = 0, max = 7) int seatIndex,
                @ForAll @IntRange(min = 1, max = 30) int cards) {
            TableSurface surface = TableSurface.forSeatCount(seats);
            int seat = seatIndex % surface.seatCount();
            Rect one = surface.handFan(seat, 1).get(0).where();
            assertThat(one.width()).isLessThanOrEqualTo((int) Math.ceil(surface.cardWidthOn(seat)));
            assertThat(one.height()).isGreaterThan(one.width());
            for (FeltHand.Slot slot : surface.handFan(seat, cards)) {
                assertThat(slot.where().width()).isEqualTo(one.width());
                assertThat(slot.where().height()).isEqualTo(one.height());
            }
        }

        /**
         * A fan, turned toward the player holding it: the cards splay away from them and meet
         * toward them, and each one along is further round.
         * <p>The same geometry as the fan in a seated body's hand, where the cards meet at the fist.
         * Laid the other way - ends further from the player than the middle - the cards cross
         * over each other and the fan reads as a bowtie.
         */
        @Property(tries = 100)
        @Label("the cards meet toward their player and splay toward the mat")
        void theFanMeetsTowardItsPlayer(@ForAll @IntRange(min = 3, max = 10) int cards) {
            TableSurface surface = TableSurface.forSeatCount(2);
            for (int seat = 0; seat < 2; seat++) {
                List<FeltHand.Slot> fan = surface.handFan(seat, cards);
                // Toward this seat's player is down the surface for a seat facing the usual way.
                double toward = surface.isTurned(seat) ? -1 : 1;
                FeltHand.Slot first = fan.get(0);
                FeltHand.Slot middle = fan.get(cards / 2);
                FeltHand.Slot last = fan.get(cards - 1);
                assertThat(toward * (first.where().centerY() - middle.where().centerY()))
                        .as("seat %s: the first card's end of the fan comes round toward its player", seat)
                        .isGreaterThan(0);
                assertThat(toward * (last.where().centerY() - middle.where().centerY()))
                        .as("seat %s: the last card's end of the fan comes round toward its player", seat)
                        .isGreaterThan(0);
                // The last card - the one on top - is the furthest round clockwise, and on the side
                // a clockwise turn leans its top toward, which is the player's right.
                for (int at = 1; at < fan.size(); at++) {
                    assertThat(fan.get(at).angle()).isGreaterThan(fan.get(at - 1).angle());
                    assertThat(toward * (fan.get(at).where().centerX() - fan.get(at - 1).where().centerX()))
                            .as("seat %s: card %s of %s is to the right of the one before it", seat, at, cards)
                            .isGreaterThanOrEqualTo(0);
                }
                assertThat(toward * (last.where().centerX() - first.where().centerX())).isGreaterThan(0);
            }
        }

        /**
         * A seat laid out the other way up holds the same fan turned half round about the middle of
         * its strip - exactly what happens to a card on its mat - so from the chair opposite, each
         * hand is the other's.
         */
        @Property(tries = 200)
        @Label("a turned seat's fan is the other one turned half round")
        void aTurnedFanIsTheSameFanTurned(@ForAll @IntRange(min = 1, max = 12) int cards) {
            Rect band = new Rect(416, 9584, 9168, 416);
            List<FeltHand.Slot> facing = FeltHand.of(band, false, cards, 611.2);
            List<FeltHand.Slot> turned = FeltHand.of(band, true, cards, 611.2);

            assertThat(turned).hasSameSizeAs(facing);
            for (int at = 0; at < facing.size(); at++) {
                Rect one = facing.get(at).where();
                Rect other = turned.get(at).where();
                // The angle is the seat's own: the half turn is the seat's facing, added by a painter.
                assertThat(other.width()).isEqualTo(one.width());
                assertThat(other.height()).isEqualTo(one.height());
                assertThat(turned.get(at).angle()).isEqualTo(facing.get(at).angle());
                assertThat(other.centerX() + one.centerX()).isCloseTo(band.centerX() * 2,
                        org.assertj.core.api.Assertions.within(1.0));
                assertThat(other.centerY() + one.centerY()).isCloseTo(band.centerY() * 2,
                        org.assertj.core.api.Assertions.within(1.0));
            }
        }

        /**
         * Worked out once and shared: both boards ask for every seat on every frame, and the fan for
         * a band and a count never changes.
         */
        @Test
        @DisplayName("a fan is worked out once and then remembered")
        void aFanIsRemembered() {
            TableSurface surface = TableSurface.forSeatCount(4);
            // The seated board builds its own surface rather than asking for the shared one, so an
            // equal band is enough to find the fan again.
            TableSurface rebuilt = TableSurface.forSeats(TableCluster.assumedSeating(4));
            assertThat(surface.handFan(2, 7)).isSameAs(surface.handFan(2, 7));
            assertThat(rebuilt.handFan(2, 7)).isSameAs(surface.handFan(2, 7));
        }
    }

    /**
     * The board on the block frames every hand too: as deep as the table and, both ways from its
     * middle, as far past its edge as the furthest hand reaches.
     */
    @Test
    @DisplayName("the board on the block frames every hand")
    void theBlockFramesEveryHand() {
        for (int tables = 1; tables <= 4; tables++) {
            TableTop top = TableTop.forCluster(0, 64, 0, tables, 1, false);
            TableSurface surface = TableSurface.forSeatCount(tables * TableCluster.SEATS_PER_TABLE);
            double down = TableFraming.everythingDown(top, surface);
            Rect reach = surface.handReach();
            // From the middle of the table, the furthest a hand reaches either way.
            double furthest = Math.max(surface.height() / 2.0 - reach.y(),
                    reach.bottom() - surface.height() / 2.0);
            assertThat(down / 2).as("%s tables", tables)
                    .isGreaterThanOrEqualTo(top.blocks(furthest) - 1e-9);
            assertThat(down).isGreaterThan(TableFraming.everythingDown(top));
        }
    }

    /** The two boards place a hand from one answer: the block's and the seated screen's agree. */
    @Test
    @DisplayName("both boards put a hand in the same place on the table")
    void bothBoardsAgree() {
        SurfaceBoard block = new SurfaceBoard(TableCluster.assumedSeating(2));
        BoardGeometry seated = new BoardGeometry(TableCluster.assumedSeating(2), 854, 480);
        for (int seat = 0; seat < 2; seat++) {
            assertThat(block.surface().handFan(seat, 7)).isEqualTo(seated.surface().handFan(seat, 7));
            assertThat(block.handEdgeRect(new SeatId(seat))).isEqualTo(block.surface().handEdge(seat));
        }
    }
}
