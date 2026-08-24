package dev.gathering.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text that fits the space it was given.
 *
 * <p>Minecraft's font draws at one size and runs straight off the end of whatever it does
 * not fit in, and in a mod full of card names - which are arbitrary text a player did not
 * choose and cannot shorten - that is not an edge case. So text shrinks to fit, and only
 * when shrinking any further would stop it being readable does it lose its tail to an
 * ellipsis instead.
 *
 * <p>Client-only.
 */
public final class GuiText {

    /** Below this the font stops being legible, so trimming beats shrinking. */
    private static final float MINIMUM_SCALE = 0.6f;

    private static final String ELLIPSIS = "...";

    private GuiText() {
    }

    /** Draws left-aligned at {@code x}, shrinking or trimming to sit inside {@code maxWidth}. */
    public static void draw(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int colour) {
        drawFitted(graphics, font, text, x, y, maxWidth, colour);
    }

    /** Draws centred on {@code centreX}, shrinking or trimming to sit inside {@code maxWidth}. */
    public static void drawCentred(
            GuiGraphics graphics, Font font, Component text, int centreX, int y, int maxWidth, int colour) {
        drawFitted(graphics, font, text, centreX - width(font, text, maxWidth) / 2, y, maxWidth, colour);
    }

    /** How wide this text will actually be drawn, once fitted. */
    public static int width(Font font, Component text, int maxWidth) {
        return Math.min(font.width(text), Math.max(0, maxWidth));
    }

    /**
     * How many lines this text needs at this width, so a panel can be built to hold it.
     *
     * <p>Asked of the same wrapping {@link #drawWrapped} does, so a panel is never a line
     * short of the words it is about to be given.
     */
    public static int linesNeeded(Font font, Component text, int maxWidth) {
        return Math.max(1, font.split(text, Math.max(1, maxWidth)).size());
    }

    /** Draws text over as many lines as it needs, breaking on words. */
    public static void drawWrapped(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int colour) {
        int line = y;
        for (FormattedCharSequence row : font.split(text, Math.max(1, maxWidth))) {
            graphics.drawString(font, row, x, line, colour, false);
            line += font.lineHeight + 1;
        }
    }

    /**
     * Whether this text would be drawn whole in {@code maxWidth}, rather than losing its tail.
     *
     * <p>For the callers whose answer to "it does not fit" is to leave it out altogether. A
     * card name has to appear somehow, so it is shrunk and trimmed; a label on the felt naming
     * a zone does not, and half a zone's name reads worse than none of it.
     */
    public static boolean fits(Font font, Component text, int maxWidth) {
        int width = font.width(text);
        return width == 0 || width * MINIMUM_SCALE <= maxWidth;
    }

    /**
     * Works in {@link FormattedText} rather than in plain strings, so a bold or coloured
     * component still arrives bold or coloured after being shrunk or trimmed - flattening to
     * a string here would quietly drop the styling a caller went to the trouble of adding.
     */
    private static void drawFitted(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int colour) {
        if (maxWidth <= 0) {
            return;
        }
        int width = font.width(text);
        if (width == 0) {
            return;
        }
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }

        float scale = Math.max(MINIMUM_SCALE, (float) maxWidth / width);
        FormattedText shown = text;
        if (width * scale > maxWidth) {
            // Even at the smallest readable size it does not fit, so the tail goes.
            int room = Math.round(maxWidth / scale) - font.width(ELLIPSIS);
            shown = FormattedText.composite(
                    font.substrByWidth(text, Math.max(0, room)), FormattedText.of(ELLIPSIS));
        }
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(shown);

        graphics.pose().pushPose();
        // Keep the shrunken line on the same baseline the full-size one would have used,
        // so a shrunk row does not sit visibly higher than its neighbours.
        graphics.pose().translate(x, y + (font.lineHeight - font.lineHeight * scale) / 2f, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, sequence, 0, 0, colour, false);
        graphics.pose().popPose();
    }
}
