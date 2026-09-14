package dev.gathering.core.game;

/**
 * Rule 103.8a: in a two-player game, the player who plays first skips the draw of their first
 * turn. In every other game nobody does (103.8c).
 * <p>A reminder and nothing more. The table draws for nobody, so there is no draw to skip; this
 * only says, to the one player it applies to, that the card they are about to take is one the
 * rules leave on the library.
 */
public final class FirstDraw {

    private FirstDraw() {
    }

    /**
     * Whether this seat is the one skipping its first draw right now.
     *
     * @param players how many players are in the game - boards with somebody's name on them
     * @param turn    the turn as it stands
     * @param seat    whose screen is asking
     */
    public static boolean isSkippedBy(int players, TurnMarker turn, SeatId seat) {
        return players == 2 && turn != null && seat != null
                && turn.turnNumber() == 1 && turn.activeSeat().equals(seat);
    }
}
