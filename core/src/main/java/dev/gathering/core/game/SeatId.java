package dev.gathering.core.game;

/**
 * A seat at the table, as the session knows it.
 * <p>Seats are registrations, not chair locks: a seated player can walk off and heckle from
 * behind someone's shoulder without losing their seat, and logging out does not drop it.
 * The session holds a seat until the player leaves it or the session ends.
 * <p>Deliberately not a player UUID. The seat outlives any particular connection, and the
 * pure core has no business knowing what a Minecraft player is.
 */
public record SeatId(int index) implements Comparable<SeatId> {

    public SeatId {
        if (index < 0) {
            throw new IllegalArgumentException("Seat index must not be negative: " + index);
        }
    }

    public static SeatId of(int index) {
        return new SeatId(index);
    }

    @Override
    public int compareTo(SeatId other) {
        return Integer.compare(index, other.index);
    }

    @Override
    public String toString() {
        return "seat" + index;
    }
}
