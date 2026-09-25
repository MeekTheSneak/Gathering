package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.GameFixtures;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
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
        return cornersOf(slot.where(), slot.angle() + facing);
    }

    /** The four corners of a rectangle turned this far about its middle, as x and y pairs. */
    private static double[][] cornersOf(Rect card, int degrees) {
        double turn = Math.toRadians(degrees);
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
            // And a hand shown face up, which is turned to whoever reads it: either way up.
            for (FeltHand.Slot slot : surface.handRow(seat, cards)) {
                for (int reader : new int[] {0, 180}) {
                    for (double[] corner : cornersOf(slot, reader)) {
                        assertThat(corner[0]).as("a corner of the face %s in %s", slot, band)
                                .isBetween((double) band.x(), (double) band.right());
                        assertThat(corner[1]).as("a corner of the face %s in %s", slot, band)
                                .isBetween((double) band.y(), (double) band.bottom());
                    }
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
            assertThat(surface.handRow(1, cards)).hasSize(Math.min(cards, FeltHand.MOST_SHOWN));
            if (cards > FeltHand.MOST_SHOWN) {
                assertThat(fan).isEqualTo(surface.handFan(1, FeltHand.MOST_SHOWN));
                assertThat(surface.handRow(1, cards)).isEqualTo(surface.handRow(1, FeltHand.MOST_SHOWN));
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
         * A hand shown to you is laid out to be read: a row, none of it turned, each card two thirds
         * of a card along from the one before, so every card's name and most of its picture are
         * clear of the next - the step the flat board always gave a shown hand.
         * <p>Fanned like the backs, each card turned about its own middle a few hundredths of a card
         * from the one under it, and only the top card of a shown hand could be read.
         */
        @Property(tries = 200)
        @Label("a hand shown face up is a row that can be read")
        void aShownHandCanBeRead(
                @ForAll @IntRange(min = 1, max = 8) int seats,
                @ForAll @IntRange(min = 0, max = 7) int seatIndex,
                @ForAll @IntRange(min = 2, max = 30) int cards) {
            TableSurface surface = TableSurface.forSeatCount(seats);
            int seat = seatIndex % surface.seatCount();
            List<FeltHand.Slot> row = surface.handRow(seat, cards);
            double toward = surface.isTurned(seat) ? -1 : 1;

            assertThat(row).hasSize(Math.min(cards, FeltHand.MOST_SHOWN));
            for (int at = 0; at < row.size(); at++) {
                Rect card = row.get(at).where();
                assertThat(row.get(at).angle()).as("face %s of %s is turned", at, cards).isZero();
                assertThat(card.centerY()).as("face %s of %s is off the row", at, cards)
                        .isEqualTo(row.get(0).where().centerY());
                if (at > 0) {
                    double along = toward * (card.centerX() - row.get(at - 1).where().centerX());
                    assertThat(along).as("face %s of %s along from the one before it", at, cards)
                            .isGreaterThanOrEqualTo(card.width() * 2 / 3.0 - 1);
                }
            }
            // One size with the fan, and a row of one is where a card drawn lands.
            assertThat(row.get(0).where().width()).isEqualTo(surface.handFan(seat, cards).get(0).where().width());
            assertThat(surface.handRow(seat, 1).get(0).where()).isEqualTo(surface.handEdge(seat));
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

    /**
     * Both boards draw every hand from one answer, {@link BoardPlacement#handsOnTheFelt}, and it puts
     * each card in the same place on the table and the same way round relative to the table.
     * <p>The flat board looks at the table through a camera that is turned half round for the far
     * chair, so a card's rectangle on it is the block's put through that camera, and its angle is
     * the block's plus that half turn. Asked of real views - the viewer's own seat left out, a
     * hand shown to them as faces turned to them - from every chair of a table of two and of four,
     * and from a replay.
     */
    @Test
    @DisplayName("both boards draw each hand in the same place and the same way round")
    void bothBoardsAgree() {
        for (int seats : new int[] {2, 4}) {
            GameSession session = GameFixtures.table(seats, 20);
            for (int seat = 0; seat < seats; seat++) {
                session.submit(new GameEvent.CardsDrawn(SeatId.of(seat), SeatId.of(seat), 3 + seat));
            }
            TableSurface surface = new SurfaceBoard(TableCluster.assumedSeating(seats)).surface();
            for (int shown = 0; shown < 2; shown++) {
                if (shown == 1) {
                    // Every hand turned toward the whole table.
                    for (int seat = 0; seat < seats; seat++) {
                        session.submit(new GameEvent.HandShown(SeatId.of(seat), null, true));
                    }
                }
                for (int chair = 0; chair < seats; chair++) {
                    SeatId viewer = SeatId.of(chair);
                    boolean faces = shown == 1;
                    GameView view = VisibilityRules.viewFor(session.state(), new Viewer.Seated(viewer));
                    SurfaceBoard block = new SurfaceBoard(TableCluster.assumedSeating(seats));
                    BoardGeometry flat = new BoardGeometry(TableCluster.assumedSeating(seats), 854, 480);
                    flat.seenFrom(viewer);
                    int cameraTurn = surface.facingDegrees(viewer.index());
                    List<BoardPlacement.HandOnTheFelt> onBlock = block.handsOnTheFelt(view);
                    List<BoardPlacement.HandOnTheFelt> onFlat = flat.handsOnTheFelt(view);

                    assertThat(onBlock).as("%s's own hand is not on the felt", viewer).hasSize(seats - 1);
                    assertThat(onFlat).hasSize(seats - 1);
                    for (int hand = 0; hand < onBlock.size(); hand++) {
                        SeatId other = onBlock.get(hand).seat().seat();
                        assertThat(other).isNotEqualTo(viewer);
                        assertThat(onFlat.get(hand).seat().seat()).isEqualTo(other);
                        int holds = session.state().contents(other, Zone.HAND).size();
                        String whose = other + "'s hand, seen by " + viewer + " at a table of " + seats;
                        theyAgree(whose, surface, flat, cameraTurn, other, holds, faces,
                                onBlock.get(hand), onFlat.get(hand));
                        // And a card drawn flies into the fan on both: to the card a hand of one is.
                        Rect one = surface.handFan(other.index(), 1).get(0).where();
                        assertThat(block.handEdgeRect(other)).as(whose).isEqualTo(one);
                        assertThat(flat.handEdgeRect(other)).as(whose).isEqualTo(flat.fromSurface(one));
                    }
                }
            }
            // A replay's view has no seat to leave out, and every hand in it face up and upright.
            GameView replay = VisibilityRules.viewFor(session.state(), new Viewer.Historian());
            BoardGeometry flat = new BoardGeometry(TableCluster.assumedSeating(seats), 854, 480);
            List<BoardPlacement.HandOnTheFelt> every = flat.handsOnTheFelt(replay);
            assertThat(every).hasSize(seats);
            for (BoardPlacement.HandOnTheFelt hand : every) {
                theyAgree(hand.seat().seat() + "'s hand in a replay", surface, flat, 0, hand.seat().seat(),
                        session.state().contents(hand.seat().seat(), Zone.HAND).size(), true,
                        new SurfaceBoard(TableCluster.assumedSeating(seats)).handsOnTheFelt(replay)
                                .get(every.indexOf(hand)),
                        hand);
            }
        }
    }

    /**
     * One hand as the block draws it and as the flat board does, against the layout: backs are the fan in
     * its order turned with their seat, faces the row turned to the reader and running to the reader's
     * right, so each face covers the right of the one under it rather than its name; and the flat board's
     * card is the block's through its camera, the same way round relative to the table.
     */
    private static void theyAgree(String whose, TableSurface surface, BoardGeometry flat, int cameraTurn,
            SeatId other, int holds, boolean faces, BoardPlacement.HandOnTheFelt onBlock,
            BoardPlacement.HandOnTheFelt onFlat) {
        assertThat(onBlock.cards()).as(whose).hasSize(Math.min(holds, FeltHand.MOST_SHOWN));
        assertThat(onBlock.faces()).as(whose).hasSize(faces ? holds : 0);
        assertThat(onFlat.faces()).as(whose).hasSize(faces ? holds : 0);
        List<FeltHand.Slot> laid = faces
                ? surface.handRow(other.index(), holds)
                : surface.handFan(other.index(), holds);
        assertThat(onBlock.cards().stream().map(BoardPlacement.HandCard::where).toList()).as(whose)
                .containsExactlyInAnyOrderElementsOf(laid.stream().map(FeltHand.Slot::where).toList());
        for (int at = 0; at < laid.size(); at++) {
            BoardPlacement.HandCard blockCard = onBlock.cards().get(at);
            BoardPlacement.HandCard flatCard = onFlat.cards().get(at);
            if (faces) {
                assertThat(Math.floorMod(blockCard.angle() - cameraTurn, 360)).as(whose).isZero();
                assertThat(Math.floorMod(flatCard.angle(), 360)).as("%s: a face upright on the screen", whose)
                        .isZero();
                if (at > 0) {
                    double right = cameraTurn == 0 ? 1 : -1;
                    assertThat(right * (blockCard.where().centerX() - onBlock.cards().get(at - 1).where().centerX()))
                            .as("%s: face %s on the block, to the reader's right of the one before", whose, at)
                            .isPositive();
                    assertThat(flatCard.where().centerX() - onFlat.cards().get(at - 1).where().centerX())
                            .as("%s: face %s on the flat board, to the right of the one before", whose, at)
                            .isPositive();
                }
            } else {
                assertThat(blockCard.where()).as(whose).isEqualTo(laid.get(at).where());
                assertThat(Math.floorMod(blockCard.angle() - laid.get(at).angle()
                        - surface.facingDegrees(other.index()), 360))
                        .as("%s: card %s on the block turned with its seat", whose, at).isZero();
            }
            assertThat(flatCard.where()).as(whose).isEqualTo(flat.fromSurface(blockCard.where()));
            assertThat(Math.floorMod(flatCard.angle() - blockCard.angle() + cameraTurn, 360))
                    .as("%s: card %s on the flat board the same way round as on the block", whose, at).isZero();
        }
    }
}
