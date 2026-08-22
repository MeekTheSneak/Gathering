package dev.gathering.core.match;

import dev.gathering.core.game.GameState;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Who, if anybody, won the game that just happened.
 *
 * <p>The mod has no rules engine, so there is exactly one thing here that counts as a result:
 * conceding. Nothing works out that somebody is dead - life reaching zero is a number reaching
 * zero, and a player at minus four who is about to gain twelve has not lost. What ends a game
 * is a player saying it has.
 *
 * <p>Which makes the arithmetic simple and worth stating in one place rather than in whichever
 * caller happened to need it: when everybody still in the game but one has conceded, the one
 * left has won it. Empty seats are not players and never win anything.
 */
public final class GameOutcome {

    private GameOutcome() {
    }

    /**
     * The seat that has won, if the game is over.
     *
     * <p>Empty while more than one player is still in - which is most of the time - and empty
     * when everybody has conceded, because a game nobody won is a drawn game and
     * {@link MatchState#afterDrawnGame()} is what that is for.
     */
    public static Optional<SeatId> winnerOf(GameState state) {
        List<SeatId> standing = standing(state);
        return standing.size() == 1 ? Optional.of(standing.get(0)) : Optional.empty();
    }

    /** Whether the game is over at all, won or drawn. */
    public static boolean isFinished(GameState state) {
        return state.ended() || playerCount(state) > 0 && standing(state).size() <= 1;
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
