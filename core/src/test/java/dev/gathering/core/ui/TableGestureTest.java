package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import org.junit.jupiter.api.Test;

/**
 * The transitions a gesture on the table can make, without a window.
 * <p>These were rules spread across four mouse handlers and a render method; each is one
 * sentence here.
 */
class TableGestureTest {

    private static TableGesture.Held offThePile(long began) {
        return new TableGesture.Held(CardInstanceId.of(1), SeatId.of(0), false, Zone.GRAVEYARD,
                0, 0, 100, 100, began, false);
    }

    private static TableGesture.Held outOfTheHand(long began) {
        return new TableGesture.Held(CardInstanceId.of(2), SeatId.of(0), true, null,
                0, 0, 100, 100, began, false);
    }

    @Test
    void aPressHeldStillLongEnoughIsAskedAboutTheWholePile() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(offThePile(0L));

        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD - 1)).isFalse();
        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD)).isTrue();

        gesture.holdWhole();
        assertThat(gesture.held().whole()).isTrue();
        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD * 10)).isFalse();
    }

    /** A drag that passes back over where it started is still a drag. */
    @Test
    void aPressThatWanderedNeverBecomesAHoldEvenWhenItComesHome() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(offThePile(0L));

        gesture.dragged(100 + TableGesture.DRAG_THRESHOLD, 100);
        gesture.dragged(100, 100);

        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD * 10)).isFalse();
    }

    @Test
    void wobbleInsideTheThresholdIsNotADrag() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(offThePile(0L));

        gesture.dragged(100 + TableGesture.DRAG_THRESHOLD - 1, 100 - TableGesture.DRAG_THRESHOLD + 1);

        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD)).isTrue();
    }

    @Test
    void aCardOutOfTheHandHasNoPileToHold() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(outOfTheHand(0L));
        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD * 10)).isFalse();
    }

    @Test
    void aRefusedHoldStaysRefusedForThatPress() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(offThePile(0L));
        gesture.refuseWhole();
        assertThat(gesture.isDueForAHold(TableGesture.LONG_HOLD * 10)).isFalse();
    }

    /** The next press starts clean: the last one wandering says nothing about this one. */
    @Test
    void pickingUpAgainForgetsTheLastPressWandering() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(offThePile(0L));
        gesture.dragged(500, 500);
        gesture.putDown();

        gesture.pickUp(offThePile(1_000L));
        assertThat(gesture.isDueForAHold(1_000L + TableGesture.LONG_HOLD)).isTrue();
    }

    @Test
    void puttingDownHandsBackWhatWasCarriedOnce() {
        TableGesture gesture = new TableGesture();
        TableGesture.Held card = offThePile(0L);
        gesture.pickUp(card);

        assertThat(gesture.putDown()).isEqualTo(card);
        assertThat(gesture.held()).isNull();
        assertThat(gesture.putDown()).isNull();
    }

    @Test
    void aBoxEndsOnceAndAPanEndsOnce() {
        TableGesture gesture = new TableGesture();
        gesture.startBox(4, 5);
        assertThat(gesture.boxStart()).isEqualTo(new TableGesture.Start(4, 5));
        assertThat(gesture.endBox()).isEqualTo(new TableGesture.Start(4, 5));
        assertThat(gesture.endBox()).isNull();

        gesture.startPan(1, 1);
        assertThat(gesture.isPanning()).isTrue();
        assertThat(gesture.endPan()).isTrue();
        assertThat(gesture.endPan()).isFalse();
    }

    /** Nothing half-done survives a cancel: no card, no box, no pan, no wandering. */
    @Test
    void cancellingForgetsEverything() {
        TableGesture gesture = new TableGesture();
        gesture.pickUp(offThePile(0L));
        gesture.dragged(900, 900);
        gesture.startBox(1, 2);
        gesture.startPan(3, 4);

        gesture.cancel();

        assertThat(gesture.held()).isNull();
        assertThat(gesture.boxStart()).isNull();
        assertThat(gesture.isPanning()).isFalse();
    }
}
