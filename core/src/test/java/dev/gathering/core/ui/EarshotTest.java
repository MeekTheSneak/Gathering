package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the turn coming round has to be brought to the player.
 * <p>It used to be played at the table and drawn only inside the table's own screen - so it reached
 * somebody sitting there looking at the board, which is the one person who did not need telling, and
 * missed somebody who had walked off to a chest, which is what the notification is for.
 */
@DisplayName("A table's earshot")
class EarshotTest {

    @Test
    @DisplayName("covers the people sitting at it and a little around it")
    void atheTableSpeaksForItselfNearby() {
        assertThat(Earshot.carries(0)).isTrue();
        assertThat(Earshot.carries(3 * 3)).isTrue();
        assertThat(Earshot.carries(Earshot.BLOCKS * Earshot.BLOCKS)).isTrue();
        assertThat(Earshot.mustFollowThePlayer(2 * 2)).isFalse();
    }

    @Test
    @DisplayName("stops where a sound played at a block stops")
    void pastItTheTurnHasToFollowThePlayer() {
        // Somebody at their chest across the room, down a mineshaft, or anywhere else they wander
        // off to while keeping their seat - which the design says they may.
        assertThat(Earshot.carries(20 * 20)).isFalse();
        assertThat(Earshot.mustFollowThePlayer(20 * 20)).isTrue();
        assertThat(Earshot.mustFollowThePlayer(200 * 200)).isTrue();
    }
}
