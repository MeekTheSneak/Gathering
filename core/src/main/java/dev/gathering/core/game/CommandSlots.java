package dev.gathering.core.game;

import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;

/**
 * Which command slot a card sent to the command zone belongs in.
 *
 * <p>There are two of them, for the deck whose commander is a pair, and "the command zone" as
 * a destination stopped being a single answer the moment there were. Sending a partner home
 * to the first slot when the first slot already holds the other one puts two cards in one
 * box and leaves the other empty - which is the stack of two under a single number that
 * having two slots exists to prevent.
 *
 * <p>So: the first empty one. A commander is out on the battlefield and its partner is at
 * home, so the empty slot is the one the card left, and it goes back where it was without
 * anybody being asked. Dragging still puts a card in whichever slot it is dropped on, which
 * is the answer for the case this guess gets wrong.
 */
public final class CommandSlots {

    private CommandSlots() {
    }

    /**
     * The slot a card should go home to, given what this seat's slots already hold.
     *
     * <p>The first empty one, or the first slot when they are all full - a card has to land
     * somewhere, and refusing to move it would be the mod saying no about a rule.
     */
    public static Zone homeFor(SeatView seat) {
        if (seat == null) {
            return Zone.COMMAND_SLOTS.get(0);
        }
        for (Zone slot : Zone.COMMAND_SLOTS) {
            // The map rather than the accessor: asking a seat for a zone it has not got is a
            // loud failure by design, and this is building a menu entry - a menu that throws
            // takes the screen down, which is a worse answer than a sensible guess.
            ZoneView held = seat.zones().get(slot);
            if (held == null || held.count() == 0) {
                return slot;
            }
        }
        return Zone.COMMAND_SLOTS.get(0);
    }
}
