package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tearing a pack open by hand. */
class PackTearTest {

    private static final int WIDTH = 120;

    @Test
    @DisplayName("nothing tears until a corner has been taken hold of")
    void theTearStartsAtACorner() {
        PackTear pack = PackTear.unopened(WIDTH, 7L);

        // Straight to the far end without ever touching the near one.
        PackTear poked = pack.followedTo(WIDTH);

        assertThat(poked.isUntouched()).isTrue();
        assertThat(poked.torn()).isZero();
        assertThat(poked.isOpen()).isFalse();
    }

    @Test
    @DisplayName("taking hold of the corner does not itself tear anything")
    void grippingIsNotTearing() {
        PackTear held = PackTear.unopened(WIDTH, 7L).followedTo(2);

        assertThat(held.gripped()).isTrue();
        assertThat(held.isUntouched()).isFalse();
        assertThat(held.tornTo()).isLessThan(WIDTH / 6);
        assertThat(held.isOpen()).isFalse();
    }

    @Test
    @DisplayName("the tear goes where the cursor goes")
    void theTearFollowsTheCursor() {
        PackTear pack = PackTear.unopened(WIDTH, 7L).followedTo(0);

        for (int x = 0; x <= WIDTH; x += 10) {
            pack = pack.followedTo(x);
            assertThat(pack.tornTo()).as("at " + x).isEqualTo(x);
        }
        assertThat(pack.isOpen()).isTrue();
    }

    @Test
    @DisplayName("a tear never heals")
    void theTearNeverGoesBack() {
        PackTear pack = PackTear.unopened(WIDTH, 7L).followedTo(0).followedTo(90);

        PackTear wandered = pack.followedTo(30).followedTo(0).followedTo(-40);

        assertThat(wandered.tornTo()).isEqualTo(90);
        assertThat(wandered.torn()).isEqualTo(pack.torn());
    }

    @Test
    @DisplayName("a cursor past the end tears no further than the end")
    void theTearStopsAtTheEnd() {
        PackTear pack = PackTear.unopened(WIDTH, 7L).followedTo(0).followedTo(WIDTH * 5);

        assertThat(pack.torn()).isEqualTo(1f);
        assertThat(pack.tornTo()).isEqualTo(WIDTH);
        assertThat(pack.isOpen()).isTrue();
    }

    @Test
    @DisplayName("the pack is not open until the tear has nearly crossed it")
    void openingTakesTheWholeWidth() {
        PackTear pack = PackTear.unopened(WIDTH, 7L).followedTo(0);

        assertThat(pack.followedTo(WIDTH / 2).isOpen()).isFalse();
        assertThat(pack.followedTo((int) (WIDTH * (PackTear.OPEN_AT - 0.05f))).isOpen()).isFalse();
        assertThat(pack.followedTo((int) (WIDTH * PackTear.OPEN_AT) + 1).isOpen()).isTrue();
    }

    @Test
    @DisplayName("a torn edge is ragged, and the same ragged every time")
    void theEdgeIsSteady() {
        PackTear pack = PackTear.unopened(WIDTH, 7L);

        float[] once = pack.edge(24, 6f);
        float[] again = pack.edge(24, 6f);

        assertThat(once).isEqualTo(again);
        assertThat(once).hasSize(24);
        // The ends are on the line: a tear begins and finishes at the edge of the paper.
        assertThat(once[0]).isZero();
        assertThat(once[once.length - 1]).isZero();
        // And the middle is not, or it is a cut rather than a tear.
        boolean wanders = false;
        for (float offset : once) {
            assertThat(Math.abs(offset)).isLessThanOrEqualTo(6f);
            wanders |= Math.abs(offset) > 0.2f;
        }
        assertThat(wanders).as("a torn edge that never left the line").isTrue();
    }

    @Test
    @DisplayName("two packs do not tear the same way")
    void everyPackTearsItsOwnWay() {
        assertThat(PackTear.unopened(WIDTH, 1L).edge(24, 6f))
                .isNotEqualTo(PackTear.unopened(WIDTH, 2L).edge(24, 6f));
    }

    @Test
    @DisplayName("an edge with no depth to wander in is flat rather than broken")
    void anEdgeWithNoRoomIsFlat() {
        assertThat(PackTear.unopened(WIDTH, 7L).edge(8, 0f)).containsOnly(0f);
        assertThat(PackTear.unopened(WIDTH, 7L).edge(1, 6f)).hasSize(2);
    }
}
