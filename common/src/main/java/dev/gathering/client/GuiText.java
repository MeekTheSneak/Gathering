package dev.gathering.client;

import dev.gathering.core.ui.TextScale;
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
    private static final float MINIMUM_SCALE = TextScale.SMALLEST;

    /**
     * How many times something has been drawn at a scale nobody asked for.
     *
     * <p>Not a log line: a mistake like this happens every frame, and a log that says so a
     * hundred times a second is a log nobody reads. It is a number the scripted run reads at
     * the end and fails on, which is the only place that can see the whole mod being drawn.
     */
    private static int wrongScales;

    /** How many times text has been drawn at a scale outside the range anything here uses. */
    public static int wrongScales() {
        return wrongScales;
    }

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
     * The one size a whole set of labels should be drawn at, given the longest of them.
     *
     * <p>Fitting each label to the space on its own gives a set in as many sizes as it has
     * lengths - "Draw" full size beside a squashed "Mulligan" - which reads as unfinished
     * however correct each one is. The set is measured from its longest and every member is
     * drawn at that.
     */
    public static float scaleForTheSet(Font font, Component longest, int room) {
        int width = font.width(longest);
        if (width <= 0 || room <= 0 || width <= room) {
            return 1f;
        }
        return Math.max(MINIMUM_SCALE, (float) room / width);
    }

    /** Draws centred on {@code centreX} at exactly this scale, whatever the text's own width. */
    public static void drawCentredAt(
            GuiGraphics graphics, Font font, Component text, int centreX, int y,
            float scale, int colour) {
        drawAt(graphics, font, text, centreX - font.width(text) * scale / 2f, y, scale, colour);
    }

    /**
     * Draws with its right-hand end at {@code rightX}, at exactly this scale.
     *
     * <p>For a column of labels that belongs to something to one side of it. Centring each
     * one in its own box gives a column with a ragged edge against the thing it names, and
     * the shorter the word the further it sits from what it is labelling - which reads as
     * the words having been dropped in rather than laid out.
     */
    public static void drawFlushRight(
            GuiGraphics graphics, Font font, Component text, int rightX, int y,
            float scale, int colour) {
        drawAt(graphics, font, text, rightX - font.width(text) * scale, y, scale, colour);
    }

    /** Draws with its left-hand end at {@code leftX}, at exactly this scale. */
    public static void drawFlushLeft(
            GuiGraphics graphics, Font font, Component text, int leftX, int y,
            float scale, int colour) {
        drawAt(graphics, font, text, leftX, y, scale, colour);
    }

    private static void drawAt(
            GuiGraphics graphics, Font font, Component text, float x, int y,
            float scale, int colour) {
        if (!TextScale.isSane(scale)) {
            // Almost always an int width handed to the float scale parameter - the two ways
            // of drawing text here take the same number of arguments and differ in one
            // position, and Java widens the int without a word. Drawing carries on at a size
            // somebody can read, because a crash in a render loop is worse than a label an
            // eighth too small; the count is what the scripted run fails on. See TextScale.
            wrongScales++;
            scale = TextScale.sane(scale);
        }
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(text);
        graphics.pose().pushPose();
        graphics.pose().translate(
                x, y + (font.lineHeight - font.lineHeight * scale) / 2f, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, sequence, 0, 0, colour, false);
        graphics.pose().popPose();
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
