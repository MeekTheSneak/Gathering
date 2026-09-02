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
public record MatchState(MatchRules rules, Map<SeatId, Integer> wins, int gameNumber) {

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

        MatchState next = new MatchState(rules, updated, gameNumber);
        return next.isDecided() ? next : new MatchState(rules, updated, gameNumber + 1);
    }

    /** A game nobody won - conceded by everyone, or abandoned - still uses one up. */
    public MatchState afterDrawnGame() {
        return gameNumber >= rules.bestOf() ? this : new MatchState(rules, wins, gameNumber + 1);
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
