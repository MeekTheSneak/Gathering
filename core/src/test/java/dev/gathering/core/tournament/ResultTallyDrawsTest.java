package dev.gathering.core.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tally may count no more drawn games than the match had room for.
 * <p>Each side's wins were bounded and the drawn games were not, so a tally that did not come off the
 * screen's own rows - which stop where {@link ResultTally#mostDraws} says - could report a best of
 * three as having had nine drawn games, and the standings would take it.
 */
@DisplayName("A match tally's drawn games")
class ResultTallyDrawsTest {

    @Test
    @DisplayName("stop where the match's own rows stop")
    void moreDrawsThanTheMatchHasRoomForAreRefused() {
        assertThat(new ResultTally(2, 0, 1).refusal(3, false)).isEmpty();
        assertThat(new ResultTally(2, 1, 1).refusal(3, false)).isEmpty();
        assertThat(new ResultTally(2, 0, 2).refusal(3, false))
                .contains(ResultTally.Refusal.TOO_MANY_GAMES);
        assertThat(new ResultTally(1, 1, 5).refusal(3, false))
                .contains(ResultTally.Refusal.TOO_MANY_GAMES);
    }

    @Test
    @DisplayName("stop further along in a longer match")
    void aLongerMatchHasRoomForMore() {
        assertThat(new ResultTally(2, 2, 2).refusal(5, false)).isEmpty();
        assertThat(new ResultTally(2, 2, 3).refusal(5, false))
                .contains(ResultTally.Refusal.TOO_MANY_GAMES);
    }
}
