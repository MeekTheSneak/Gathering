package dev.gathering.core.match;

import dev.gathering.core.game.GameState;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Who, if anybody, won the game that just happened.
 * <p>The mod has no rules engine, so there is exactly one thing here that counts as a result:
 * conceding. Nothing works out that somebody is dead - life reaching zero is a number reaching
 * zero, and a player at minus four who is about to gain twelve has not lost. What ends a game
 * is a player saying it has.
 * <p>Which makes the arithmetic simple and worth stating in one place rather than in whichever
 * caller happened to need it: when everybody still in the game but one has conceded, the one
 * left has won it. Empty seats are not players and never win anything.
 */
public final class GameOutcome {

    private GameOutcome() {
    }

    /**
     * The seat that has won, if the game is over.
     * <p>Empty while more than one player is still in - which is most of the time - and empty
     * when everybody has conceded, because a game nobody won is a drawn game and
     * {@link MatchState#afterDrawnGame()} is what that is for.
     * <p><b>Winning by outlasting requires somebody to outlast.</b> One player left standing
     * out of one is not a victory, it is a person sitting at a table on their own, and calling
     * it a win ends a solo game the instant it starts.
     */
    public static Optional<SeatId> winnerOf(GameState state) {
        List<SeatId> standing = standing(state);
        return standing.size() == 1 && playerCount(state) >= 2
                ? Optional.of(standing.get(0))
                : Optional.empty();
    }

    /**
     * Whether the game is over at all, won or drawn.
     * <p>Three ways, and the third is the one that is easy to leave out. The session has been
     * ended outright; everybody who was playing has conceded; or one player is left out of
     * two or more. A table with a single player at it is none of those until they concede,
     * which is what makes goldfishing possible - and goldfishing is how anybody tests a deck,
     * so a mod that cannot do it cannot be used alone at all.
     */
    public static boolean isFinished(GameState state) {
        if (state.ended()) {
            return true;
        }
        int players = playerCount(state);
        if (players == 0) {
            return false;
        }
        int standing = standing(state).size();
        return standing == 0 || players >= 2 && standing == 1;
    }

    /** Occupied seats that have not conceded. */
    private static List<SeatId> standing(GameState state) {
        List<SeatId> standing = new ArrayList<>();
        for (SeatId seat : state.seats()) {
            SeatState seatState = state.seatState(seat);
            if (seatState.isOccupied() && !seatState.conceded()) {
                standing.add(seat);
            }
        }
        return standing;
    }

    private static int playerCount(GameState state) {
        int players = 0;
        for (SeatId seat : state.seats()) {
            if (state.seatState(seat).isOccupied()) {
                players++;
            }
        }
        return players;
    }
}
