package dev.gathering.core.tournament;

/**
 * How a match ended, in games: what each player won, and how many games were drawn.
 * <p>Games, not a winner, because the tiebreakers are counted in games and because a match
 * called at time is decided on games won - a player up one game to none when time runs out
 * has won the match, with the unfinished game counted as a draw.
 *
 * @param winsA games the first player won
 * @param winsB games the second player won
 * @param draws games nobody won, including one unfinished at time
 */
public record MatchResult(int winsA, int winsB, int draws) {

    public MatchResult {
        if (winsA < 0 || winsB < 0 || draws < 0 || winsA + winsB + draws > 9) {
            throw new IllegalArgumentException("Not a match: " + winsA + "-" + winsB + "-" + draws);
        }
    }

    /** What a bye is worth: a match won two games to none, against nobody. */
    public static final MatchResult BYE = new MatchResult(2, 0, 0);

    /** What dropping mid-round concedes: the match, two games to none. */
    public static MatchResult conceded(boolean firstPlayerConcedes) {
        return firstPlayerConcedes ? new MatchResult(0, 2, 0) : new MatchResult(2, 0, 0);
    }

    public boolean firstWon() {
        return winsA > winsB;
    }

    public boolean secondWon() {
        return winsB > winsA;
    }

    public boolean isDraw() {
        return winsA == winsB;
    }

    public int games() {
        return winsA + winsB + draws;
    }

    /** The same match, seen from the other chair. */
    public MatchResult flipped() {
        return new MatchResult(winsB, winsA, draws);
    }

    /**
     * A match called at time: what each player had won, and the game in progress as a draw -
     * unless the players had not started another game, which is no game at all.
     */
    public static MatchResult atTime(int winsA, int winsB, boolean gameInProgress) {
        return new MatchResult(winsA, winsB, gameInProgress ? 1 : 0);
    }
}
