package dev.gathering.core.game;

/**
 * Whose turn it is, and which turn of the game this is.
 * <p>Handed on manually by whoever is playing. A group that forgets to pass it has a slightly
 * stale marker, which is exactly what happens with a paper turn marker, and is a far better
 * failure than a mod that decides the turn has ended.
 * <p>There was a phase here too - untap, upkeep, draw, and the nine after them - as a shared
 * marker the active player advanced by hand. It is gone. Nothing ever read it: no action was
 * checked against it and nothing was ever stopped, so it was a label the table maintained for
 * the mod's benefit rather than its own. The tables people already play on do not have one
 * either, and a group that wants to announce a step says so out loud, which is what they were
 * doing anyway. Passing the turn is the whole of the structure now.
 */
public record TurnMarker(SeatId activeSeat, int turnNumber) {

    public TurnMarker {
        if (activeSeat == null) {
            throw new IllegalArgumentException("A turn marker needs an active seat");
        }
        if (turnNumber < 1) {
            throw new IllegalArgumentException("Turn numbers start at 1, got " + turnNumber);
        }
    }

    public static TurnMarker start(SeatId firstSeat) {
        return new TurnMarker(firstSeat, 1);
    }

    /** Hands the turn to the next seat in the seating order. */
    public TurnMarker passTo(SeatId nextSeat) {
        return new TurnMarker(nextSeat, turnNumber + 1);
    }
}
