package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * Where the pot sits, as geometry.
 * <p>The one thing on the table that belongs to nobody, so it lies beside the table rather than
 * on it. What is worth pinning down is that it stays off every seat's board however many seats
 * and however many cards, and that showing the whole table still shows it.
 */
class TablePotTest {

    private static TableSurface twoSeats() {
        return TableSurface.forSeatCount(2);
    }

    @Test
    void aTableWithNothingStakedSetsNoRoomAside() {
        assertThat(twoSeats().pot(0).isEmpty()).isTrue();
        assertThat(twoSeats().pot(-1).isEmpty()).isTrue();
    }

    /**
     * Past the east edge of the whole table, level with its middle.
     * <p>The fault this exists for: laid across the middle of the table the pot sat where the
     * life totals are and where cards played toward the middle go, so it was always in the way
     * of something. Measured against the surface, which is every table in the cluster, so a
     * table pushed against the east side moves it out rather than putting a mat under it.
     */
    @Property
    void thePotLiesBesideTheTableHoweverManySeats(
            @ForAll @IntRange(min = 1, max = 8) int seats,
            @ForAll @IntRange(min = 1, max = 40) int howMany) {
        TableSurface surface = TableSurface.forSeatCount(seats);
        Rect pot = surface.pot(howMany);
        assertThat(pot.isEmpty()).isFalse();
        Rect tray = TableSurface.potTray(pot);
        assertThat(tray.x()).isGreaterThan(surface.width());
        int centerY = tray.y() + tray.height() / 2;
        assertThat(Math.abs(centerY - surface.height() / 2)).isLessThanOrEqualTo(1);
    }

    /** Nothing a seat owns is under it: no mat, no life total, no hand held at a mat's edge. */
    @Property
    void thePotCoversNothingASeatOwns(
            @ForAll @IntRange(min = 1, max = 8) int seats,
            @ForAll @IntRange(min = 1, max = 40) int howMany) {
        TableSurface surface = TableSurface.forSeatCount(seats);
        Rect tray = TableSurface.potTray(surface.pot(howMany));
        for (int seat = 0; seat < seats; seat++) {
            assertThat(tray.overlaps(surface.matOf(seat)))
                    .as("pot tray " + tray + " over seat " + seat + "'s mat").isFalse();
            Rect life = surface.lifeBox(seat);
            assertThat(!life.isEmpty() && tray.overlaps(life))
                    .as("pot tray " + tray + " over seat " + seat + "'s life box " + life)
                    .isFalse();
            Rect hand = surface.handEdge(seat);
            // The widest fan a hand is drawn as: ten cards, two thirds of a card apart.
            int fan = hand.width() * 2 / 3 * 9 + hand.width();
            Rect fanned = new Rect(hand.x() + hand.width() / 2 - fan / 2, hand.y(),
                    fan, hand.height());
            assertThat(tray.overlaps(fanned))
                    .as("pot tray " + tray + " over seat " + seat + "'s hand " + fanned)
                    .isFalse();
        }
    }

    /** However many cards, the column stays within the table's own depth. */
    @Property
    void theColumnNeverRunsPastTheTable(
            @ForAll @IntRange(min = 1, max = 8) int seats,
            @ForAll @IntRange(min = 1, max = 60) int howMany) {
        TableSurface surface = TableSurface.forSeatCount(seats);
        Rect pot = surface.pot(howMany);
        Rect tray = TableSurface.potTray(pot);
        assertThat(tray.y()).isGreaterThanOrEqualTo(0);
        assertThat(tray.bottom()).isLessThanOrEqualTo(surface.height());
        for (int index = 0; index < howMany; index++) {
            Rect slot = TableSurface.potSlot(pot, index, howMany);
            assertThat(slot.isEmpty()).isFalse();
            assertThat(tray.contains(slot.x(), slot.y())
                    && tray.contains(slot.right() - 1, slot.bottom() - 1))
                    .as("card " + index + " " + slot + " outside its tray " + tray).isTrue();
        }
    }

    @Test
    void oneCardIsCenteredRatherThanPushedToOneEnd() {
        Rect pot = twoSeats().pot(1);
        Rect only = TableSurface.potSlot(pot, 0, 1);
        assertThat(Math.abs((only.y() + only.height() / 2) - (pot.y() + pot.height() / 2)))
                .isLessThanOrEqualTo(1);
    }

    @Test
    void aBigPotLeansRatherThanRunningOffTheTable() {
        TableSurface surface = twoSeats();
        Rect few = surface.pot(2);
        Rect many = surface.pot(40);
        // The column stops growing once it has taken the share of the table it is allowed.
        assertThat(many.height()).isGreaterThanOrEqualTo(few.height());
        assertThat(many.height()).isLessThan(surface.height());
    }

    /** Staked cards the size of the ones on one table, however deep the cluster is. */
    @Test
    void aDeeperClusterDoesNotStakeBiggerCards() {
        assertThat(TableSurface.forSeatCount(8).pot(1).height())
                .isEqualTo(twoSeats().pot(1).height());
    }

    /** Cards run top to bottom in the order they were staked, never backwards. */
    @Property
    void thePotReadsInOrder(@ForAll @IntRange(min = 2, max = 40) int howMany) {
        Rect pot = twoSeats().pot(howMany);
        int previous = Integer.MIN_VALUE;
        for (int index = 0; index < howMany; index++) {
            int y = TableSurface.potSlot(pot, index, howMany).y();
            assertThat(y).isGreaterThan(previous);
            previous = y;
        }
    }

    /**
     * Showing the whole table shows the pot too, from either side of it.
     * <p>Beside the table is off the screen at a framing fitted to the table alone, so the one
     * view a player reaches for to find it has to take it in.
     */
    @Property
    void showingEverythingIncludesThePot(
            @ForAll @IntRange(min = 1, max = 8) int seats,
            @ForAll @IntRange(min = 1, max = 40) int howMany,
            @ForAll boolean farSide) {
        TableSurface surface = TableSurface.forSeatCount(seats);
        Rect tray = TableSurface.potTray(surface.pot(howMany));
        int width = 1280;
        int height = 720;
        TableCamera camera = TableCamera.showingAll(
                surface.width(), surface.height(), tray, width, height).seenFrom(farSide);
        if (camera.isAtFurthest()) {
            // Zoomed out as far as a card stays readable, and a cluster this big still does
            // not fit this window - the table alone would not either, pot or no pot.
            return;
        }
        for (double x : new double[] {0, surface.width(), tray.x(), tray.right()}) {
            double onScreen = camera.toScreenX(x, width);
            assertThat(onScreen).isBetween(0.0, (double) width);
        }
        for (double y : new double[] {0, surface.height(), tray.y(), tray.bottom()}) {
            double onScreen = camera.toScreenY(y, height);
            assertThat(onScreen).isBetween(0.0, (double) height);
        }
    }

    @Test
    void askingForACardThatIsNotInThePotGetsNothing() {
        Rect pot = twoSeats().pot(3);
        assertThat(TableSurface.potSlot(pot, 3, 3).isEmpty()).isTrue();
        assertThat(TableSurface.potSlot(pot, -1, 3).isEmpty()).isTrue();
        assertThat(TableSurface.potSlot(Rect.NONE, 0, 1).isEmpty()).isTrue();
    }
}
