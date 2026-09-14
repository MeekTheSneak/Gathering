package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Rule 103.8: the first player skips their first draw in a two-player game, and only then. */
class FirstDrawTest {

    @Test
    void onlyTheFirstPlayerOfATwoPlayerGameOnTheirFirstTurn() {
        SeatId first = SeatId.of(1);
        SeatId second = SeatId.of(0);
        TurnMarker opening = TurnMarker.start(first);
        assertThat(FirstDraw.isSkippedBy(2, opening, first)).isTrue();
        assertThat(FirstDraw.isSkippedBy(2, opening, second)).isFalse();
        // The second player's first turn is turn two, and they draw.
        assertThat(FirstDraw.isSkippedBy(2, opening.passTo(second), second)).isFalse();
        assertThat(FirstDraw.isSkippedBy(2, opening.passTo(second).passTo(first), first)).isFalse();
        // 103.8c: in a multiplayer game nobody skips.
        assertThat(FirstDraw.isSkippedBy(3, opening, first)).isFalse();
        assertThat(FirstDraw.isSkippedBy(1, opening, first)).isFalse();
    }
}
