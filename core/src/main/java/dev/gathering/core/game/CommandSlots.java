package dev.gathering.core.game;

import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;

/**
 * Which command slot a card sent to the command zone belongs in.
 * <p>There are two of them, for the deck whose commander is a pair, and "the command zone" as
 * a destination stopped being a single answer the moment there were. Sending a partner home
 * to the first slot when the first slot already holds the other one puts two cards in one
 * box and leaves the other empty - which is the stack of two under a single number that
 * having two slots exists to prevent.
 * <p>So: the first empty one. A commander is out on the battlefield and its partner is at
 * home, so the empty slot is the one the card left, and it goes back where it was without
 * anybody being asked. Dragging still puts a card in whichever slot it is dropped on, which
 * is the answer for the case this guess gets wrong.
 */
public final class CommandSlots {

    private CommandSlots() {
    }

    /**
     * What each cast already made adds to a commander's next cost.
     * <p>The game records casts, because casts are what the rule counts; a player reads mana,
     * because mana is what they are about to pay. One number turns one into the other, and it
     * lives here so the screen that lists taxes and the slot that shows one cannot disagree
     * about what a cast is worth.
     */
    public static final int MANA_PER_CAST = 2;

    /** What a commander cast this many times costs on top of its printed cost. */
    public static int taxFor(int casts) {
        return Math.max(0, casts) * MANA_PER_CAST;
    }

    /**
     * The commander sitting in one of a seat's command slots, or null.
     * <p>The top card, and only if it is one this viewer may see - a face-down card in a
     * command slot has no name to tax. Null rather than empty for the two cases that are the
     * same to a caller: no such slot, and a slot with nothing in it.
     */
    public static CardInstanceId commanderIn(SeatView seat, Zone slot) {
        if (seat == null || slot == null || !slot.isCommandSlot()) {
            return null;
        }
        ZoneView held = seat.zones().get(slot);
        if (held == null || held.cards().isEmpty()) {
            return null;
        }
        // Index 0 is the top: it is where place(TOP) writes and where topOf reads. The
        // last index was the other end, which only agreed while a slot held one card.
        return held.cards().get(0) instanceof CardView.Visible visible
                ? visible.id()
                : null;
    }

    /**
     * The slot a card should go home to, given what this seat's slots already hold.
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
