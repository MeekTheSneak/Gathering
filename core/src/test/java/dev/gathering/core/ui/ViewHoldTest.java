package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a view is put back to while a card is being read, and what the mouse still does.
 * <p>Reported as "holding alt while holding a card in your hand needs to lock your camera
 * movement".
 */
class ViewHoldTest {

    /** The turn that counts as the whole gesture, in degrees. */
    private static final float MOST = 22f;

    @Test
    @DisplayName("holds the view exactly where it was when the read started")
    void theViewDoesNotMove() {
        ViewHold hold = new ViewHold(MOST);
        Facing started = Facing.of(37.5f, -12f);
        assertThat(hold.frame(true, started)).contains(started);
        // The mouse drags the view off, frame after frame; it is put back every time.
        Facing dragged = started;
        for (int frame = 0; frame < 120; frame++) {
            dragged = Facing.of(started.yaw() + 3f, started.pitch() + 1f);
            assertThat(hold.frame(true, dragged))
                    .as("frame %d", frame)
                    .contains(started);
        }
    }

    @Test
    @DisplayName("adds the mouse up into the card's turn instead of the world's")
    void theGestureStillCounts() {
        ViewHold hold = new ViewHold(MOST);
        Facing started = Facing.of(0f, 0f);
        hold.frame(true, started);
        hold.frame(true, Facing.of(5f, 2f));
        assertThat(hold.turnedYaw()).isEqualTo(5f);
        assertThat(hold.turnedPitch()).isEqualTo(2f);
        hold.frame(true, Facing.of(5f, 2f));
        assertThat(hold.turnedYaw()).isEqualTo(10f);
        assertThat(hold.turnedPitch()).isEqualTo(4f);
    }

    @Test
    @DisplayName("stops counting at the far end of the gesture and never runs away")
    void theGestureHasAnEnd() {
        ViewHold hold = new ViewHold(MOST);
        hold.frame(true, Facing.of(0f, 0f));
        for (int frame = 0; frame < 500; frame++) {
            hold.frame(true, Facing.of(9f, 9f));
        }
        assertThat(hold.turnedYaw()).isEqualTo(MOST);
        assertThat(hold.turnedPitch()).isEqualTo(MOST);
        for (int frame = 0; frame < 1000; frame++) {
            hold.frame(true, Facing.of(-9f, -9f));
        }
        assertThat(hold.turnedYaw()).isEqualTo(-MOST);
    }

    @Test
    @DisplayName("counts a turn across north as a small turn, not most of a circle")
    void acrossNorth() {
        ViewHold hold = new ViewHold(MOST);
        hold.frame(true, Facing.of(179f, 0f));
        hold.frame(true, Facing.of(-177f, 0f));
        assertThat(hold.turnedYaw()).isEqualTo(4f);
    }

    @Test
    @DisplayName("lets go the moment the key does, and keeps nothing")
    void lettingGo() {
        ViewHold hold = new ViewHold(MOST);
        hold.frame(true, Facing.of(20f, 0f));
        hold.frame(true, Facing.of(28f, 0f));
        assertThat(hold.isHolding()).isTrue();

        assertThat(hold.frame(false, Facing.of(28f, 0f))).isEmpty();
        assertThat(hold.isHolding()).isFalse();
        assertThat(hold.turnedYaw()).isZero();
        assertThat(hold.turnedPitch()).isZero();

        // And the next read anchors where the player is now, not where the last one started.
        assertThat(hold.frame(true, Facing.of(100f, 5f))).contains(Facing.of(100f, 5f));
    }

    @Test
    @DisplayName("holds nothing when there is no view to hold")
    void noPlayer() {
        ViewHold hold = new ViewHold(MOST);
        assertThat(hold.frame(true, null)).isEmpty();
        assertThat(hold.isHolding()).isFalse();
    }

    @Test
    @DisplayName("keeps the view it was given, however that view was written down")
    void aViewIsPutBackTheWayItWasTaken() {
        ViewHold hold = new ViewHold(MOST);
        Optional<Facing> put = hold.frame(true, Facing.of(370f, 0f));
        assertThat(put).contains(Facing.of(10f, 0f));
    }
}
