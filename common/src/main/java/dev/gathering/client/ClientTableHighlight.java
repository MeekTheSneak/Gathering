package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
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

    private ClientTableHighlight() {
    }

    public static void set(CardInstanceId under, List<CardInstanceId> picked) {
        hovered = under;
        selected = List.copyOf(picked);
    }

    /** Cleared when the in-world view closes, so a ring never outlives the cursor that made it. */
    public static void clear() {
        hovered = null;
        selected = List.of();
    }

    public static boolean isLit(CardInstanceId card) {
        return card != null && (card.equals(hovered) || selected.contains(card));
    }
}
