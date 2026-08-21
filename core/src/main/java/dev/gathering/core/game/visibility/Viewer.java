package dev.gathering.core.game.visibility;

import dev.gathering.core.game.SeatId;
import java.util.Optional;

/**
 * Somebody looking at the table.
 *
 * <p>Two kinds, and the difference is entirely about entitlement rather than about what the
 * interface looks like. A spectator is not a seated player with fewer buttons; it is a
 * different set of payloads.
 */
public sealed interface Viewer {

    record Seated(SeatId seat) implements Viewer {
    }

    /**
     * Anyone watching: a player standing near the table, or someone on an arena's broadcast
     * spectate camera.
     *
     * <p>A spectating client receives exactly the public payload set and nothing else, so it
     * is incapable of leaking a hand even if modified - it was never sent one.
     */
    record Spectator() implements Viewer {
    }

    Viewer SPECTATOR = new Spectator();

    static Viewer seat(SeatId seat) {
        return new Seated(seat);
    }

    default Optional<SeatId> seatId() {
        return this instanceof Seated seated ? Optional.of(seated.seat()) : Optional.empty();
    }

    default boolean isSeatedAt(SeatId seat) {
        return this instanceof Seated seated && seated.seat().equals(seat);
    }
}
