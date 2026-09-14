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

    /** The most games any match here has: a best of five with every game drawn is fewer. */
    public static final int MOST_GAMES = 9;

    public MatchResult {
        // Each count bounded before any are added: three counts near the top of an int add up
        // to a negative number, which a single check on the total let through.
        if (winsA < 0 || winsB < 0 || draws < 0 || winsA > MOST_GAMES || winsB > MOST_GAMES || draws > MOST_GAMES
                || winsA + winsB + draws > MOST_GAMES) {
            throw new IllegalArgumentException("Not a match: " + winsA + "-" + winsB + "-" + draws);
        }
    }

    /** Whether these counts make a match, without building one. */
    public static boolean isAMatch(int winsA, int winsB, int draws) {
        return winsA >= 0 && winsB >= 0 && draws >= 0 && winsA <= MOST_GAMES && winsB <= MOST_GAMES
                && draws <= MOST_GAMES && winsA + winsB + draws <= MOST_GAMES;
    }

    /**
     * Whether a match of this length can end this way. Nobody wins more games than it takes to
     * win the match, and both players cannot have won it; drawn games and a match called at
     * time are still any shape short of that.
     */
    public boolean fits(int bestOf) {
        int toWin = bestOf / 2 + 1;
        return winsA <= toWin && winsB <= toWin && !(winsA == toWin && winsB == toWin);
    }

    /**
     * The results a match of this length is offered as, from the first chair, most common first.
     * <p>Every way a real match ends, not only the ones played out: won at time a game up with no
     * game started (1-0), drawn at one game each, drawn with the first game unfinished (0-0-1),
     * and 0-0 for a draw the players agreed before playing. A cut cannot end in a draw, so none
     * is offered there - a button the server is only going to refuse is a dead end.
     */
    public static java.util.List<MatchResult> offered(int bestOf, boolean elimination) {
        int[][] shapes = bestOf <= 1
                ? new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, 0}}
                : bestOf <= 3
                        ? new int[][] {{2, 0, 0}, {2, 1, 0}, {1, 2, 0}, {0, 2, 0}, {1, 0, 0}, {0, 1, 0},
                                {1, 0, 1}, {0, 1, 1}, {1, 1, 0}, {1, 1, 1}, {0, 0, 1}, {0, 0, 0}}
                        : new int[][] {{3, 0, 0}, {3, 1, 0}, {3, 2, 0}, {2, 3, 0}, {1, 3, 0}, {0, 3, 0},
                                {2, 1, 0}, {1, 2, 0}, {2, 2, 0}, {2, 2, 1}, {1, 1, 0}, {0, 0, 0}};
        java.util.List<MatchResult> offered = new java.util.ArrayList<>();
        for (int[] shape : shapes) {
            MatchResult result = new MatchResult(shape[0], shape[1], shape[2]);
            if (result.fits(bestOf) && !(elimination && result.isDraw())) {
                offered.add(result);
            }
        }
        return java.util.List.copyOf(offered);
    }

    /** How a result is written: games won each way, then drawn games when there were any. */
    public String label() {
        return winsA + "-" + winsB + (draws > 0 ? "-" + draws : "");
    }

    /** What a bye is worth: a match won two games to none, against nobody. */
    public static final MatchResult BYE = new MatchResult(2, 0, 0);

    /**
     * What dropping mid-round concedes: the match, by as many games as it takes to win one of
     * this length and none back - one in a best of one, which two to none is not a result of.
     */
    public static MatchResult conceded(boolean firstPlayerConcedes, int bestOf) {
        int toWin = bestOf / 2 + 1;
        return firstPlayerConcedes ? new MatchResult(0, toWin, 0) : new MatchResult(toWin, 0, 0);
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
