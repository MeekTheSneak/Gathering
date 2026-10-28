package dev.gathering.core.ui;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;

/**
 * What the pointer is in the middle of doing on the table: carrying a card, dragging out a
 * selection box, or panning.
 * <p>The table screen used to keep this as four fields among a hundred - the card in the air,
 * whether the press had wandered, where a box started, where a pan started - each set in one
 * handler, read in three others and cleared in however many places remembered to. Which
 * transitions were possible was a property of the whole six-thousand-line file. Here each one
 * is a method, the fields change nowhere else, and the rules between them - a press that
 * wandered can never become a long hold, putting down is also forgetting - are tested
 * directly rather than inferred from a mouse handler.
 * <p>Pure: coordinates are integers and time is a number handed in, so nothing here needs a
 * window. What a gesture <em>means</em> - where a card lands, what a box selects - stays with
 * the screen, which has the board.
 */
public final class TableGesture {

    /** How far the pointer moves, in pixels either way, before a press counts as a drag. */
    public static final int DRAG_THRESHOLD = 3;

    /**
     * How long a press has to be held still before it means the whole pile, in milliseconds.
     * <p>Long enough that nobody picks up their graveyard by accident on the way to picking
     * up the top card of it, short enough that it is a gesture rather than a wait. Measured
     * against the same monotonic clock the card flights use.
     */
    public static final long LONG_HOLD = 350L;

    /**
     * A card that has been picked up.
     * <p>The grab offset is the whole reason this is a record rather than an id: a card that
     * snaps its corner to the cursor jumps out from under your finger the moment you touch it,
     * and putting something down where you are pointing is the one thing a table has to get
     * right.
     * <p>grabX and grabY are in the space the board is measured in - pixels on the seated
     * screen, surface units on the block. pressX and pressY stay in pixels, because how far
     * the hand has moved before a press becomes a drag is a question about the mouse.
     * <p>fromPile is the zone the card was lifted off, or null. A press on a pile cannot know
     * yet whether it is a click or the start of a drag, so it becomes a drag either way and the
     * release decides: moved, and the card goes where it was dropped; not moved, and it was a
     * click on the pile after all.
     * <p>card is null only for a pile whose top this client may not name - your own library.
     * There is still something to hold there, because holding a library is how you pick the
     * whole thing up, and the whole thing needs no card named to move.
     * <p>whole is set once the press has been held still long enough to mean the pile or the
     * stack rather than the card off the top of it. began is when the press landed, on the
     * same monotonic clock everything else in the client measures against.
     */
    public record Held(
            CardInstanceId card, SeatId from, boolean fromHand, Zone fromPile,
            int grabX, int grabY, int pressX, int pressY, long began, boolean whole) {

        public boolean hasMoved(int mouseX, int mouseY) {
            return Math.abs(mouseX - pressX) >= DRAG_THRESHOLD
                    || Math.abs(mouseY - pressY) >= DRAG_THRESHOLD;
        }

        Held asWhole() {
            return new Held(card, from, fromHand, fromPile, grabX, grabY, pressX, pressY, began, true);
        }
    }

    /** A point a box or a pan started from. */
    public record Start(int x, int y) {
    }

    private Held held;

    /**
     * Whether the press currently down ever wandered, or was ruled out of becoming a hold.
     * <p>Latched, rather than asked of the cursor's position each frame: a drag that happens
     * to pass back over the point it started from is still a drag, and must not turn into a
     * long hold three seconds in because the hand came home.
     */
    private boolean strayed;

    private Start boxFrom;
    private Start panFrom;

    // ------------------------------------------------------------------ carrying

    /** The card in the air, or null. */
    public Held held() {
        return held;
    }

    /** Takes hold of a card, a new press with no history. */
    public void pickUp(Held card) {
        held = card;
        strayed = false;
    }

    /**
     * The pointer moved with a button down. A carried card that has gone past the drag
     * threshold is a drag from now on, whatever the pointer does next.
     */
    public void dragged(int x, int y) {
        if (held != null && !held.whole() && held.hasMoved(x, y)) {
            strayed = true;
        }
    }

    /**
     * Whether the press has been held still long enough to be asked whether it means the whole
     * pile or stack. Never for a card out of the hand, which has no pile under it, and never
     * once the press has wandered or been refused.
     */
    public boolean isDueForAHold(long now) {
        return held != null && !held.whole() && !held.fromHand() && !strayed
                && now - held.began() >= LONG_HOLD;
    }

    /** The press means the whole pile or stack now. */
    public void holdWhole() {
        if (held != null) {
            held = held.asWhole();
        }
    }

    /** There was nothing whole to hold here; this press stays a card, however long it lasts. */
    public void refuseWhole() {
        strayed = true;
    }

    /** Lets go: what was carried, which is no longer carried. Null if nothing was. */
    public Held putDown() {
        Held was = held;
        held = null;
        return was;
    }

    // ------------------------------------------------------------------ boxing

    /** Starts dragging out a selection box here. */
    public void startBox(int x, int y) {
        boxFrom = new Start(x, y);
    }

    /** Where the box being dragged out started, or null. */
    public Start boxStart() {
        return boxFrom;
    }

    /** Finishes the box: where it started, which is no longer being dragged. Null if none. */
    public Start endBox() {
        Start was = boxFrom;
        boxFrom = null;
        return was;
    }

    // ------------------------------------------------------------------ panning

    public void startPan(int x, int y) {
        panFrom = new Start(x, y);
    }

    public boolean isPanning() {
        return panFrom != null;
    }

    /** Stops panning; whether a pan was going on. */
    public boolean endPan() {
        boolean was = panFrom != null;
        panFrom = null;
        return was;
    }

    // ------------------------------------------------------------------ all of it

    /**
     * Forgets every gesture at once.
     * <p>For the moments nothing half-done may survive: the screen re-initialized under a
     * prompt, or the board swapped for the one in the world. A card still held after either
     * would be dropped, or a box consumed, by the next unrelated release.
     */
    public void cancel() {
        held = null;
        strayed = false;
        boxFrom = null;
        panFrom = null;
    }
}
