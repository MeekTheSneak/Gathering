package dev.gathering.core.game;

/**
 * Whose turn it is and which phase, shown in every view including the roam HUD.
 *
 * <p>Advanced manually by the active player. A group that forgets to advance it has a
 * slightly stale marker, which is exactly what happens with a paper turn marker, and is a
 * far better failure than a mod that decides the turn has ended.
 */
public record TurnMarker(SeatId activeSeat, Phase phase, int turnNumber) {

    public TurnMarker {
        if (activeSeat == null || phase == null) {
            throw new IllegalArgumentException("A turn marker needs an active seat and a phase");
        }
        if (turnNumber < 1) {
            throw new IllegalArgumentException("Turn numbers start at 1, got " + turnNumber);
        }
    }

    public static TurnMarker start(SeatId firstSeat) {
        return new TurnMarker(firstSeat, Phase.UNTAP, 1);
    }

    public TurnMarker withPhase(Phase newPhase) {
        return new TurnMarker(activeSeat, newPhase, turnNumber);
    }

    /** Hands the turn to the next seat in the seating order and starts it at untap. */
    public TurnMarker passTo(SeatId nextSeat) {
        return new TurnMarker(nextSeat, Phase.UNTAP, turnNumber + 1);
    }
}
