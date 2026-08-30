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

    /**
     * Somebody reading a game that is over, who is entitled to all of it.
     *
     * <p><b>The one viewer that sees hidden information, and the only reason it is safe is
     * that the game has ended.</b> A replay is the disclosure moment this mod's whole
     * visibility design is built around: during play nothing hidden is ever sent, and
     * afterwards there is nothing left to protect - the hands have been played, the library
     * order cannot be exploited, and the argument about what was on top is worth settling.
     *
     * <p>Two things keep that true, and neither is a comment. It is never in
     * {@link VisibilityRules#allViews}, which is what the invariant suites iterate, so a
     * Historian cannot quietly become one of the views a live table hands out. And the only
     * code that constructs one refuses a session that has not ended.
     */
    record Historian() implements Viewer {
    }

    Viewer HISTORIAN = new Historian();

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

    /** Whether this viewer is entitled to everything, which only a finished game allows. */
    default boolean seesEverything() {
        return this instanceof Historian;
    }
}
