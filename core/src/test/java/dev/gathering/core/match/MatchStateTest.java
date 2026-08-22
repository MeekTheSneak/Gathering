package dev.gathering.core.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.SeatId;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A set of games, and when it is over.
 *
 * <p>The failures worth guarding against here are the ones that waste an evening: a match
 * that asks for a fourth game of a best-of-three, or one that stops at 1-1 and declares
 * somebody the winner.
 */
class MatchStateTest {

    private static final SeatId ALICE = new SeatId(0);
    private static final SeatId BOB = new SeatId(1);

    @Test
    @DisplayName("a single game is over as soon as it is played")
    void oneGameIsTheWholeMatch() {
        MatchState match = MatchState.beginning(MatchRules.single(FormatPresets.COMMANDER));

        // Game one is still to be played; it is the game after it that does not exist.
        assertThat(match.hasGameToPlay()).isTrue();
        MatchState afterOne = match.afterGameWonBy(ALICE);
        assertThat(afterOne.isDecided()).isTrue();
        assertThat(afterOne.hasGameToPlay()).isFalse();
    }

    @Test
    @DisplayName("best of three is won by two, not by three")
    void twoWinsTakeABestOfThree() {
        MatchState match = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3));

        MatchState afterOne = match.afterGameWonBy(ALICE);
        assertThat(afterOne.isDecided()).isFalse();
        assertThat(afterOne.gameNumber()).isEqualTo(2);
        assertThat(afterOne.hasGameToPlay()).isTrue();

        MatchState afterTwo = afterOne.afterGameWonBy(ALICE);
        assertThat(afterTwo.isDecided()).isTrue();
        assertThat(afterTwo.winner()).contains(ALICE);
        assertThat(afterTwo.hasGameToPlay()).isFalse();
        // Two-nil is over on game two; it does not advance to a game three nobody plays.
        assertThat(afterTwo.gameNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("a best of three that goes to three is decided by the third")
    void theThirdGameDecidesIt() {
        MatchState match = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3))
                .afterGameWonBy(ALICE)
                .afterGameWonBy(BOB);

        assertThat(match.isDecided()).isFalse();
        assertThat(match.gameNumber()).isEqualTo(3);
        assertThat(match.hasGameToPlay()).isTrue();
        assertThat(match.sideboardingBeforeNextGame()).isTrue();

        assertThat(match.afterGameWonBy(BOB).winner()).contains(BOB);
    }

    @Test
    @DisplayName("Commander does not sideboard, however long the set")
    void sideboardingFollowsTheFormat() {
        // Not because best-of-three is meaningless there, but because the format has no
        // sideboard to bring in from.
        assertThat(new MatchRules(FormatPresets.COMMANDER, 3).hasSideboarding()).isFalse();
        assertThat(new MatchRules(FormatPresets.MODERN, 3).hasSideboarding()).isTrue();
        assertThat(MatchRules.single(FormatPresets.MODERN).hasSideboarding()).isFalse();
    }

    @Test
    @DisplayName("an even-length match is refused, because a drawn set settles nothing")
    void onlyOddLengths() {
        assertThatThrownBy(() -> new MatchRules(FormatPresets.MODERN, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Property(tries = 2000)
    void everyMatchEndsWithinItsLength(@ForAll("scripts") List<Integer> winners) {
        // The failure this rules out is a match that keeps asking for another game.
        MatchState match = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3));

        int played = 0;
        for (int winner : winners) {
            if (!match.hasGameToPlay()) {
                break;
            }
            match = match.afterGameWonBy(winner % 2 == 0 ? ALICE : BOB);
            played++;
        }

        assertThat(played).isLessThanOrEqualTo(3);
        assertThat(match.gameNumber()).isBetween(1, 3);
    }

    @Property(tries = 2000)
    void nobodyEverWinsMoreGamesThanItTakes(@ForAll("scripts") List<Integer> winners) {
        MatchState match = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 5));

        for (int winner : winners) {
            if (!match.hasGameToPlay()) {
                break;
            }
            match = match.afterGameWonBy(winner % 2 == 0 ? ALICE : BOB);
        }

        assertThat(match.winsFor(ALICE) + match.winsFor(BOB)).isLessThanOrEqualTo(5);
        assertThat(match.winsFor(ALICE)).isLessThanOrEqualTo(match.rules().gamesToWin());
        assertThat(match.winsFor(BOB)).isLessThanOrEqualTo(match.rules().gamesToWin());
    }

    @Property(tries = 1000)
    void aDecidedMatchNeverAsksForAnotherGame(@ForAll("scripts") List<Integer> winners) {
        MatchState match = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3));
        for (int winner : winners) {
            match = match.afterGameWonBy(winner % 2 == 0 ? ALICE : BOB);
            if (match.isDecided()) {
                assertThat(match.hasGameToPlay()).isFalse();
                assertThat(match.sideboardingBeforeNextGame()).isFalse();
                return;
            }
        }
    }

    @Property(tries = 2000)
    void aBestOfThreeAlwaysGetsItsThirdGameWhenItNeedsOne(@ForAll("scripts") List<Integer> winners) {
        // The bug this pins down: at one game each the match is on game three, which nobody
        // has played, and asking whether the game number has passed the length says it is
        // over. A best-of-three that stops at 1-1 is an evening ruined.
        MatchState match = MatchState.beginning(new MatchRules(FormatPresets.MODERN, 3))
                .afterGameWonBy(ALICE)
                .afterGameWonBy(BOB);

        assertThat(match.isDecided()).isFalse();
        assertThat(match.hasGameToPlay()).isTrue();
        assertThat(match.afterGameWonBy(winners.get(0) % 2 == 0 ? ALICE : BOB).isDecided()).isTrue();
    }

    @Provide
    Arbitrary<List<Integer>> scripts() {
        return Arbitraries.integers().between(0, 1).list().ofMinSize(1).ofMaxSize(10);
    }
}
