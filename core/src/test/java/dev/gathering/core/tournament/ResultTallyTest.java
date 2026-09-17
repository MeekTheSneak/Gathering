package dev.gathering.core.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultTallyTest {

    @Test
    @DisplayName("every result the old buttons offered can be counted out, and nothing they refused can be sent")
    void theCountsReachWhatTheButtonsDid() {
        for (int bestOf : new int[] {1, 3, 5}) {
            for (boolean elimination : new boolean[] {false, true}) {
                Set<MatchResult> reachable = new HashSet<>();
                for (int mine = 0; mine <= ResultTally.mostWins(bestOf); mine++) {
                    for (int theirs = 0; theirs <= ResultTally.mostWins(bestOf); theirs++) {
                        for (int draws = 0; draws <= ResultTally.mostDraws(bestOf); draws++) {
                            new ResultTally(mine, theirs, draws).result(bestOf, elimination).ifPresent(result -> {
                                assertThat(result.fits(bestOf)).isTrue();
                                assertThat(elimination && result.isDraw()).isFalse();
                                reachable.add(result);
                            });
                        }
                    }
                }
                assertThat(reachable).as("best of %s, elimination %s", bestOf, elimination)
                        .containsAll(MatchResult.offered(bestOf, elimination));
            }
        }
    }

    @Test
    @DisplayName("a tally says why it cannot be sent")
    void refusalsSayWhy() {
        assertThat(new ResultTally(2, 2, 0).refusal(3, false)).contains(ResultTally.Refusal.BOTH_WON);
        assertThat(new ResultTally(1, 1, 0).refusal(3, true)).contains(ResultTally.Refusal.DRAWN_IN_A_CUT);
        assertThat(new ResultTally(0, 0, 0).refusal(3, false)).isEmpty();
        assertThat(new ResultTally(3, 0, 0).refusal(3, false)).contains(ResultTally.Refusal.TOO_MANY_GAMES);
        assertThat(new ResultTally(5, 4, 1).refusal(5, false)).contains(ResultTally.Refusal.TOO_MANY_GAMES);
    }

    @Test
    @DisplayName("the counts start from the player's report, then the opponent's, then the table's")
    void startsFromWhatIsAlreadyKnown() {
        assertThat(ResultTally.startingFrom("2-1", "1-2", "2-0")).isEqualTo(new ResultTally(2, 1, 0));
        assertThat(ResultTally.startingFrom("", "1-2", "2-0")).isEqualTo(new ResultTally(1, 2, 0));
        assertThat(ResultTally.startingFrom("", "", "1-0-1")).isEqualTo(new ResultTally(1, 0, 1));
        assertThat(ResultTally.startingFrom("", "", "")).isEqualTo(ResultTally.NONE);
        assertThat(new ResultTally(2, 1, 1).label()).isEqualTo("2-1-1");
    }
}
