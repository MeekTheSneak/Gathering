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

    /**
     * The card currently following the cursor, which is drawn on the screen and not on the
     * table.
     *
     * <p>A card being dragged has not moved yet - the server has not been told, and will not
     * be until it lands - so the board still lists it wherever it came from. Drawing it there
     * as well leaves a copy lying on the felt while its twin follows the cursor, which reads
     * as the drag having failed.
     */
    private static CardInstanceId inTheAir;

    /** The zone a card is currently being held over, so it can light up as a target. */
    private static SeatId aimedSeat;
    private static int aimedPile = -1;

    /** Whose mat a card in the air would land on, zone or no zone. */
    private static SeatId landing;

    private ClientTableHighlight() {
    }

    public static void set(CardInstanceId under, List<CardInstanceId> picked, CardInstanceId held) {
        hovered = under;
        selected = List.copyOf(picked);
        inTheAir = held;
    }

    /** Whether this card is the one in the player's hand rather than on the table. */
    public static boolean isInTheAir(CardInstanceId card) {
        return card != null && card.equals(inTheAir);
    }

    /** Which zone a dragged card would go into, so the table can say so before it is let go. */
    public static void aimAt(SeatId seat, int pile) {
        aimedSeat = pile < 0 ? null : seat;
        aimedPile = pile;
    }

    /**
     * Whose mat a dragged card would land on, zone or no zone.
     *
     * <p>Separate from the zone it is aimed at, because most of a mat is not a zone and a card
     * let go over bare felt still lands on somebody's side of the table. Without it the board
     * drawn on the block said nothing at all about where a card was going unless the cursor
     * happened to be over one of the four small boxes in the corner.
     */
    public static void landingOn(SeatId seat) {
        landing = seat;
    }

    public static boolean isLandingOn(SeatId seat) {
        return seat != null && seat.equals(landing);
    }

    /** Cleared when the in-world view closes, so a ring never outlives the cursor that made it. */
    public static void clear() {
        hovered = null;
        selected = List.of();
        inTheAir = null;
        aimedSeat = null;
        aimedPile = -1;
        landing = null;
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
