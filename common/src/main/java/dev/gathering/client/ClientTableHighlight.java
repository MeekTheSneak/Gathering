package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import java.util.List;

/**
 * What the cursor is on, for the table in the world to draw a ring around.
 *
 * <p>Hovering and selecting are the screen's business: they come out of a mouse position and a
 * click, and neither of those things exists as far as a block entity renderer is concerned. So
 * the screen works them out, as it always has, and leaves the answer here for the renderer to
 * pick up on its next frame.
 *
 * <p>One player's own idea about their own screen, never sent anywhere - the same as the
 * selection it carries. Nothing here can be true of anybody else's client.
 *
 * <p>Client-only.
 */
public final class ClientTableHighlight {

    private static CardInstanceId hovered;
    private static List<CardInstanceId> selected = List.of();

    /** The zone a card is currently being held over, so it can light up as a target. */
    private static SeatId aimedSeat;
    private static int aimedPile = -1;

    private ClientTableHighlight() {
    }

    public static void set(CardInstanceId under, List<CardInstanceId> picked) {
        hovered = under;
        selected = List.copyOf(picked);
    }

    /** Which zone a dragged card would go into, so the table can say so before it is let go. */
    public static void aimAt(SeatId seat, int pile) {
        aimedSeat = pile < 0 ? null : seat;
        aimedPile = pile;
    }

    /** Cleared when the in-world view closes, so a ring never outlives the cursor that made it. */
    public static void clear() {
        hovered = null;
        selected = List.of();
        aimedSeat = null;
        aimedPile = -1;
    }

    public static boolean isLit(CardInstanceId card) {
        return card != null && (card.equals(hovered) || selected.contains(card));
    }

    /** Whether anything at all is lit. For the scripted harness, which cannot see a ring. */
    static boolean isLitAtAll() {
        return hovered != null;
    }

    public static boolean isAimedAt(SeatId seat, int pile) {
        return aimedPile >= 0 && aimedPile == pile && seat != null && seat.equals(aimedSeat);
    }
}
