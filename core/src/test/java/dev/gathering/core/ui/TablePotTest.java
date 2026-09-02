package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * Where the pot sits, as geometry.
 * <p>The one rectangle on the table that belongs to nobody, so the thing worth pinning down is
 * that it stays in the middle and stays on the table however many cards end up in it.
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

    @Test
    void thePotIsInTheMiddleBandOfTheTable() {
        TableSurface surface = twoSeats();
        Rect pot = surface.pot(2);
        int centerY = pot.y() + pot.height() / 2;
        // Vertically it is where the mats meet. Horizontally it goes wherever there is room,
        // which is not the exact middle when somebody's life total is sitting there.
        assertThat(Math.abs(centerY - surface.height() / 2))
                .isLessThanOrEqualTo(pot.height());
    }

    /**
     * The pot never covers anything anybody has to read.
     * <p>The fault this exists for: a life box sits on the edge of a mat facing the middle of
     * the table, which is the same strip of table the pot wants, so a pot drawn centered lands
     * straight on top of one. Checked at every seat count and every size, against the whole
     * tray rather than just the cards - a tray checked at one size and drawn at another is a
     * tray nobody tested.
     */
    @Property
    void thePotNeverCoversALifeTotal(
            @ForAll @IntRange(min = 2, max = 8) int seats,
            @ForAll @IntRange(min = 1, max = 40) int howMany) {
        TableSurface surface = TableSurface.forSeatCount(seats);
        Rect pot = surface.pot(howMany);
        if (pot.isEmpty()) {
            return;
        }
        Rect tray = TableSurface.potTray(pot);
        for (int seat = 0; seat < seats; seat++) {
            Rect life = surface.lifeBox(seat);
            if (!life.isEmpty()) {
                assertThat(tray.overlaps(life))
                        .as("pot tray " + tray + " over seat " + seat + "'s life box " + life)
                        .isFalse();
            }
            Rect verbs = surface.verbGroup(seat, dev.gathering.core.ui.TableVerb.count());
            if (!verbs.isEmpty()) {
                assertThat(tray.overlaps(verbs))
                        .as("pot tray " + tray + " over seat " + seat + "'s verbs " + verbs)
                        .isFalse();
            }
        }
    }

    /** And whatever it avoids, the whole tray is still on the table. */
    @Property
    void theWholeTrayStaysOnTheTable(
            @ForAll @IntRange(min = 2, max = 8) int seats,
            @ForAll @IntRange(min = 1, max = 40) int howMany) {
        TableSurface surface = TableSurface.forSeatCount(seats);
        Rect pot = surface.pot(howMany);
        if (pot.isEmpty()) {
            return;
        }
        Rect tray = TableSurface.potTray(pot);
        assertThat(tray.x()).isGreaterThanOrEqualTo(0);
        assertThat(tray.y()).isGreaterThanOrEqualTo(0);
        assertThat(tray.right()).isLessThanOrEqualTo(surface.width());
        assertThat(tray.bottom()).isLessThanOrEqualTo(surface.height());
    }

    @Test
    void oneCardIsCenteredRatherThanPushedToOneEnd() {
        TableSurface surface = twoSeats();
        Rect pot = surface.pot(1);
        Rect only = TableSurface.potSlot(pot, 0, 1);
        assertThat(Math.abs((only.x() + only.width() / 2) - (pot.x() + pot.width() / 2)))
                .isLessThanOrEqualTo(1);
    }

    @Test
    void aBigPotLeansRatherThanRunningOffTheTable() {
        TableSurface surface = twoSeats();
        Rect few = surface.pot(2);
        Rect many = surface.pot(40);
        // The row stops growing once it has taken the share of the table it is allowed.
        assertThat(many.width()).isGreaterThanOrEqualTo(few.width());
        assertThat(many.right()).isLessThanOrEqualTo(surface.width());
        assertThat(many.x()).isGreaterThanOrEqualTo(0);
    }

    /** However many cards are in it, every one of them is drawn on the table. */
    @Property
    void everyCardInThePotIsOnTheTable(@ForAll @IntRange(min = 1, max = 60) int howMany) {
        TableSurface surface = twoSeats();
        Rect pot = surface.pot(howMany);
        assertThat(pot.isEmpty()).isFalse();

        for (int index = 0; index < howMany; index++) {
            Rect slot = TableSurface.potSlot(pot, index, howMany);
            assertThat(slot.isEmpty()).isFalse();
            assertThat(slot.x()).isGreaterThanOrEqualTo(0);
            assertThat(slot.y()).isGreaterThanOrEqualTo(0);
            assertThat(slot.right()).isLessThanOrEqualTo(surface.width());
            assertThat(slot.bottom()).isLessThanOrEqualTo(surface.height());
        }
    }

    /** Cards run left to right in the order they were staked, never backwards. */
    @Property
    void thePotReadsInOrder(@ForAll @IntRange(min = 2, max = 40) int howMany) {
        Rect pot = twoSeats().pot(howMany);
        int previous = Integer.MIN_VALUE;
        for (int index = 0; index < howMany; index++) {
            int x = TableSurface.potSlot(pot, index, howMany).x();
            assertThat(x).isGreaterThan(previous);
            previous = x;
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
