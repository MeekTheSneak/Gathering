package dev.gathering.client;

import dev.gathering.core.card.Sleeve;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing the back of a card, in whatever the deck it came from is sleeved in.
 * <p>One place, because a face-down card is drawn in nine or ten of them - a pile's top, a
 * hand seen from across the table, a stack being carried, a permanent turned over - and a
 * sleeve that only some of those knew about would be a table where a player's cards changed
 * appearance depending on where they were sitting.
 * <p>How a sleeve is built: one gray texture, multiplied by the sleeve's color, with the
 * game's own item or block art printed on it for the ones that carry a picture. That is why
 * there are two files rather than twenty-five - and why the pictures are Minecraft's rather
 * than ours, which is the only art in the mod that is not.
 * <p>Client-only.
 */
public final class CardSleeves {

    /** The gray sleeve every colored one is a tint of. */
    private static final ResourceLocation PLAIN = ResourceLocation.fromNamespaceAndPath(
            dev.gathering.Gathering.MOD_ID, "textures/card/sleeve.png");

    /**
     * How much of the card's width the printed picture takes.
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
            rounded(graphics, CardFaceRenderer.CARD_BACK, x, y, width, height);
            return;
        }

        int tint = drawn.tint();
        graphics.setColor(
                ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1f);
        rounded(graphics, PLAIN, x, y, width, height);
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

    /**
     * How round a card's corner is, as a fraction of its width.
     * <p>A Magic card is 63mm across with a corner of about 3mm, which is where this comes from.
     * A fraction rather than a number of pixels, because the same card is drawn at a dozen sizes
     * on one screen and a fixed radius would be a nick on the big one and a bite out of the small.
     */
    private static final float CORNER = 3f / 63f;

    /**
     * Draws a card back with its corners taken off.
     * <p>The two textures are square, and they are the owner's, so the shape is made here. A face
     * has come rounded all along - the art arrives with its corners already transparent - so a
     * face-down card beside a face-up one was the only square thing on the table.
     * <p>Drawn as a stack of rows: the middle in one piece, and each row of a corner as its own
     * slice inset by how far the curve has come in. That is a handful of extra draws on a card
     * rather than a mesh, which matters because a board can hold sixty face-down cards and this
     * runs on every one of them every frame.
     */
    private static void rounded(GuiGraphics graphics, ResourceLocation texture,
            int x, int y, int width, int height) {
        int radius = radiusFor(width, height);
        if (radius == 0) {
            // Too small for a corner to be anything but a missing pixel.
            graphics.blit(texture, x, y, 0f, 0f, width, height, width, height);
            return;
        }
        // Everything between the curves, which on any card worth drawing is nearly all of it.
        graphics.blit(texture, x, y + radius, 0f, radius, width, height - radius * 2, width, height);
        for (int row = 0; row < radius; row++) {
            int inset = insetAt(radius, row);
            int across = width - inset * 2;
            if (across <= 0) {
                continue;
            }
            graphics.blit(texture, x + inset, y + row, inset, row, across, 1, width, height);
            int fromBottom = height - 1 - row;
            graphics.blit(texture, x + inset, y + fromBottom, inset, fromBottom, across, 1,
                    width, height);
        }
    }

    /**
     * How far in the curve has come at this row of a corner, in pixels.
     * <p>The quarter circle the corner is, measured at the middle of the row rather than its edge,
     * so a one-pixel radius takes one pixel off and not two. Row nought is the outermost.
     */
    static int insetAt(int radius, int row) {
        // Measured at the middle of the row, and rounded rather than floored, so the innermost
        // row of the curve comes out flush with the card's side. Floored, it stopped a pixel
        // short all the way down and the card had a hairline notch instead of a corner.
        double up = radius - row - 0.5;
        return radius - (int) Math.round(Math.sqrt(Math.max(0d, (double) radius * radius - up * up)));
    }

    /**
     * How round a card of this size is drawn, in pixels, or nought if it is too small to round.
     * <p>Two is the smallest corner worth having. One takes nothing off at all - the curve of a
     * single pixel is a single pixel - and would cost two extra draws a card to do it.
     */
    static int radiusFor(int width, int height) {
        int radius = Math.round(width * CORNER);
        return radius < 2 || height < radius * 2 || width < radius * 2 ? 0 : radius;
    }

    /**
     * The picture printed on a sleeve, as a resource. Vanilla's, named by the sleeve itself.
     * <p>Parsed once per sleeve. This is asked for every face-down card with an emblem every
     * frame, and parsing splits the string and validates every character of it - sixty cards
     * face down was sixty of those a frame for an answer that never changes.
     */
    public static ResourceLocation emblem(Sleeve sleeve) {
        return EMBLEMS.computeIfAbsent(sleeve, each -> ResourceLocation.parse(each.emblem()));
    }

    /** Each sleeve's emblem, parsed the first time it is drawn. */
    private static final java.util.Map<Sleeve, ResourceLocation> EMBLEMS =
            java.util.Collections.synchronizedMap(new java.util.EnumMap<>(Sleeve.class));
}
