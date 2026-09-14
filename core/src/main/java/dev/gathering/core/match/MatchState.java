package dev.gathering.core.match;

import dev.gathering.core.game.SeatId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * How a set of games is going.
 * <p>Kept apart from the game itself: a game is a board and a log, and a match is a score.
 * Folding the score into the session would mean a match could not outlive the game it is
 * currently on, which is precisely what it has to do.
 * <p>Nothing here is secret. Who has won how many is the most public fact at a table.
 */
public record MatchState(
        MatchRules rules, Map<SeatId, Integer> wins, int gameNumber, SeatId lastGameWinner, SeatId choseForDrawnGame) {

    /** A match that has not yet had a game won in it, or one read from before winners were kept. */
    public MatchState(MatchRules rules, Map<SeatId, Integer> wins, int gameNumber) {
        this(rules, wins, gameNumber, null, null);
    }

    /** A match read from before drawn games remembered who had chosen for them. */
    public MatchState(MatchRules rules, Map<SeatId, Integer> wins, int gameNumber, SeatId lastGameWinner) {
        this(rules, wins, gameNumber, lastGameWinner, null);
    }

    /**
     * Who plays first in the next game of a two-player match: the player who lost the last one,
     * or after a drawn game whoever chose for that game.
     * <p>The tournament rules give the loser of the previous game the choice of playing or drawing,
     * and after a draw the player who chose at the start of the drawn game chooses again (MTR 2.2).
     * Playing first is what almost everybody chooses - so the table starts them, and they can
     * pass the turn if they would rather draw. Empty for the first game, or when it is not a
     * two-player match: those start at random.
     */
    public Optional<SeatId> startsNextGame(java.util.Collection<SeatId> playing) {
        if (playing.size() != 2) {
            return Optional.empty();
        }
        if (lastGameWinner == null) {
            return Optional.ofNullable(choseForDrawnGame).filter(playing::contains);
        }
        if (!playing.contains(lastGameWinner)) {
            return Optional.empty();
        }
        return playing.stream().filter(seat -> !seat.equals(lastGameWinner)).findFirst();
    }

    public MatchState {
        // In seat order rather than a hash order salted once per launch: this is walked to
        // write the match into the save, and a score that encoded differently on every start
        // is a save file that churns for no reason.
        wins = wins == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(wins));
        if (gameNumber < 1) {
            throw new IllegalArgumentException("A match starts on game one, not " + gameNumber);
        }
    }

    public static MatchState beginning(MatchRules rules) {
        return new MatchState(rules, Map.of(), 1);
    }

    public int winsFor(SeatId seat) {
        return wins.getOrDefault(seat, 0);
    }

    /**
     * The match with one more game in the books.
     * <p>The game number advances only if another game is going to be played, so a finished
     * match reads as "game 3 of 3" rather than as a fourth game nobody played.
     */
    public MatchState afterGameWonBy(SeatId winner) {
        Map<SeatId, Integer> updated = new LinkedHashMap<>(wins);
        updated.merge(winner, 1, Integer::sum);

        MatchState next = new MatchState(rules, updated, gameNumber, winner, null);
        return next.isDecided() ? next : new MatchState(rules, updated, gameNumber + 1, winner, null);
    }

    /** A game nobody won - conceded by everyone, or abandoned - still uses one up. */
    public MatchState afterDrawnGame() {
        return afterDrawnGame(null);
    }

    /**
     * The same, remembering who chose to play or draw in the game that was drawn: they choose
     * again for the next one.
     *
     * @param whoChose the player the drawn game's first turn went to, or null if nobody was named
     */
    public MatchState afterDrawnGame(SeatId whoChose) {
        return gameNumber >= rules.bestOf() ? this : new MatchState(rules, wins, gameNumber + 1, null, whoChose);
    }

    public boolean isDecided() {
        return winner().isPresent();
    }

    public Optional<SeatId> winner() {
        return wins.entrySet().stream()
                .filter(entry -> entry.getValue() >= rules.gamesToWin())
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Whether there is a game still to be played, the current one included.
     * <p>Deliberately not "is there <em>another</em> game". Those are different questions and
     * conflating them is how a best-of-three stops after two: at one game each the match is on
     * game three, which has not been played, and a check of {@code gameNumber < bestOf} says
     * it is finished.
     * <p>False once somebody has taken the match, and false once the games have run out
     * however the wins fell - a set that has played its last game is over whether or not it
     * settled anything.
     */
    public boolean hasGameToPlay() {
        return !isDecided() && gameNumber <= rules.bestOf();
    }

    /**
     * Whether players get to change their decks before the next game.
     * <p>Sideboarding happens <em>between</em> games, so never before the first: a deck
     * arrives at the table as its owner built it.
     */
    public boolean sideboardingBeforeNextGame() {
        return gameNumber > 1 && hasGameToPlay() && rules.hasSideboarding();
    }
}
