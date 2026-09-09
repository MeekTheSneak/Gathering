package dev.gathering.core.game;

import dev.gathering.core.game.event.GameEvent;

/**
 * The events a client never writes, whatever it says its name is.
 * <p>Two kinds. Some describe the table's own lifecycle - a seat being taken, a deck going
 * down, the session ending - and every honest constructor of them is on the server. Others
 * carry a result the server rolled: a die, a coin, the planar die. Both kinds arrive at the
 * server through the same generic event packet as an ordinary move, and attribution is no
 * defence against either, because a client forging one signs it with its own seat.
 * <p>An audit put it plainly: those events "already contain their results", and a handler
 * that rolls honestly does not protect a second route that accepts a finished number. A
 * player who could send {@code DiceRolled(mySeat, 20, 20)} rolls twenty every time, and the
 * log - which is the whole evidence a table has - agrees with them.
 * <p>Lives in the pure layer so it can be tested against the sealed hierarchy itself: the
 * suite walks every permitted event type and fails on one nobody has classified, which is
 * what makes this a list that cannot silently fall behind the events.
 */
public final class ServerAuthored {

    private ServerAuthored() {
    }

    /** Whether this event is the server's to write, so a client sending it is refused. */
    public static boolean isTheServersToWrite(GameEvent event) {
        return switch (event) {
            // The table's own lifecycle. A client that could end the session ended the game
            // for everybody with none of the match, the decks or the pot put away; one that
            // could load a deck swapped its library for any cards it liked mid-game.
            case GameEvent.SessionEnded ignored -> true;
            case GameEvent.DeckLoaded ignored -> true;
            case GameEvent.SeatTaken ignored -> true;

            // Chance, already resolved. The request for a roll is a payload of its own and
            // the server answers it with the level's randomness; this is the packet that
            // skips the rolling and reports the answer.
            case GameEvent.DiceRolled ignored -> true;
            case GameEvent.CoinFlipped ignored -> true;
            case GameEvent.PlanarRolled ignored -> true;

            default -> false;
        };
    }
}
