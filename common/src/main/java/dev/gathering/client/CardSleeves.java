package dev.gathering.client;

import dev.gathering.core.card.Sleeve;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing the back of a card, in whatever the deck it came from is sleeved in.
 *
 * <p>One place, because a face-down card is drawn in nine or ten of them - a pile's top, a
 * hand seen from across the table, a stack being carried, a permanent turned over - and a
 * sleeve that only some of those knew about would be a table where a player's cards changed
 * appearance depending on where they were sitting.
 *
 * <p>How a sleeve is built: one gray texture, multiplied by the sleeve's color, with the
 * game's own item or block art printed on it for the ones that carry a picture. That is why
 * there are two files rather than twenty-five - and why the pictures are Minecraft's rather
 * than ours, which is the only art in the mod that is not.
 *
 * <p>Client-only.
 */
public final class CardSleeves {

    /** The gray sleeve every colored one is a tint of. */
    private static final ResourceLocation PLAIN = ResourceLocation.fromNamespaceAndPath(
            dev.gathering.Gathering.MOD_ID, "textures/card/sleeve.png");

    /**
     * How much of the card's width the printed picture takes.
     *
     * <p>Under half. A sleeve is read across a table at the size of a thumbnail, and a picture
     * filling the card would be a card whose picture is the only thing on it - the color, which
     * is what actually tells one player's cards from another's, would be a border.
     */
    private static final float EMBLEM_SPAN = 0.44f;

    private CardSleeves() {
    }

    /** What that seat's cards look like from behind, or the ordinary back if there is no seat. */
    public static Sleeve of(GameView board, SeatId owner) {
        if (board == null || owner == null) {
            return Sleeve.DEFAULT;
        }
        SeatView seat = board.seat(owner);
        return seat == null ? Sleeve.DEFAULT : seat.sleeve();
    }

    /** Draws the back of a card, filling that rectangle. */
    public static void draw(GuiGraphics graphics, Sleeve sleeve, int x, int y, int width, int height) {
        Sleeve drawn = sleeve == null ? Sleeve.DEFAULT : sleeve;
        if (drawn.isPrinted()) {
            graphics.blit(CardFaceRenderer.CARD_BACK, x, y, 0f, 0f, width, height, width, height);
            return;
        }

        int tint = drawn.tint();
        graphics.setColor(
                ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1f);
        graphics.blit(PLAIN, x, y, 0f, 0f, width, height, width, height);
        // Put back before the picture, which is drawn in its own colors: leaving the tint on
        // would print a red sword on a red sleeve.
        graphics.setColor(1f, 1f, 1f, 1f);

        if (!drawn.hasEmblem()) {
            return;
        }
        int span = Math.max(1, Math.round(width * EMBLEM_SPAN));
        graphics.blit(emblem(drawn), x + (width - span) / 2, y + (height - span) / 2,
                0f, 0f, span, span, span, span);
    }

    /** The picture printed on a sleeve, as a resource. Vanilla's, named by the sleeve itself. */
    public static ResourceLocation emblem(Sleeve sleeve) {
        return ResourceLocation.parse(sleeve.emblem());
    }
}
