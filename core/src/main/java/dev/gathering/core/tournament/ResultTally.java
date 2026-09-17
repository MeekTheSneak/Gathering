package dev.gathering.core.tournament;

import java.util.Optional;

/**
 * A match result as a player enters it: games each side won, and games drawn, counted one at a time.
 * <p>The way the Companion app asks for one. The screen used to offer every way a match could end as
 * its own button - sixteen for a best of three, eighteen for a best of five - and finding "2-1" among
 * them was a search. Three short rows of counts ask the same question in the order a player thinks
 * of it: how many did I win, how many did they, were any drawn.
 * <p>From the chair of whoever is entering it: {@code mine} is theirs, {@code theirs} their opponent's.
 * The host settling a table enters it from the first player's chair, which is the same shape.
 * <p>Pure.
 */
public record ResultTally(int mine, int theirs, int draws) {

    /** Why a tally cannot be sent as it stands. */
    public enum Refusal {
        /** Both sides counted as having won the match. */
        BOTH_WON,
        /** A cut match counted as drawn, which a cut cannot end in. */
        DRAWN_IN_A_CUT,
        /** More games than any match here has. */
        TOO_MANY_GAMES
    }

    public static final ResultTally NONE = new ResultTally(0, 0, 0);

    public ResultTally {
        mine = Math.max(0, mine);
        theirs = Math.max(0, theirs);
        draws = Math.max(0, draws);
    }

    /** The most games one side can be counted as winning: as many as win the match. */
    public static int mostWins(int bestOf) {
        return Math.max(1, bestOf) / 2 + 1;
    }

    /**
     * The most drawn games offered. One for a best of one or three, two for a best of five: a draw
     * is a game run out of time or a board nobody can win, and more than that is a match that
     * should go to the host.
     */
    public static int mostDraws(int bestOf) {
        return Math.max(1, Math.max(1, bestOf) / 2);
    }

    /** The tally a result makes, read from how {@link MatchResult#label} writes it, or empty. */
    public static Optional<ResultTally> of(String label) {
        return MatchResult.parse(label).map(result -> new ResultTally(result.winsA(), result.winsB(), result.draws()));
    }

    /**
     * Where a player's counts start: what they reported, else what their opponent did, else what the
     * table saw, else nothing - so confirming what is already there is one press.
     */
    public static ResultTally startingFrom(String myReport, String theirReport, String suggested) {
        return of(myReport).or(() -> of(theirReport)).or(() -> of(suggested)).orElse(NONE);
    }

    public ResultTally withMine(int count) {
        return new ResultTally(count, theirs, draws);
    }

    public ResultTally withTheirs(int count) {
        return new ResultTally(mine, count, draws);
    }

    public ResultTally withDraws(int count) {
        return new ResultTally(mine, theirs, count);
    }

    /** Why this tally cannot be sent for a match of this length, or empty when it can. */
    public Optional<Refusal> refusal(int bestOf, boolean elimination) {
        if (!MatchResult.isAMatch(mine, theirs, draws)) {
            return Optional.of(Refusal.TOO_MANY_GAMES);
        }
        MatchResult result = new MatchResult(mine, theirs, draws);
        if (!result.fits(bestOf)) {
            return Optional.of(mine > mostWins(bestOf) || theirs > mostWins(bestOf)
                    ? Refusal.TOO_MANY_GAMES : Refusal.BOTH_WON);
        }
        if (elimination && result.isDraw()) {
            return Optional.of(Refusal.DRAWN_IN_A_CUT);
        }
        return Optional.empty();
    }

    /** The result this tally sends, when it can be sent. */
    public Optional<MatchResult> result(int bestOf, boolean elimination) {
        return refusal(bestOf, elimination).isPresent()
                ? Optional.empty()
                : Optional.of(new MatchResult(mine, theirs, draws));
    }

    /** How the result is written, the way reports are shown. */
    public String label() {
        return mine + "-" + theirs + (draws > 0 ? "-" + draws : "");
    }
}
