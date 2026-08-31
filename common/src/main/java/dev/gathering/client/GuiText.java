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

    /**
     * Which of the mod's own lines have had their tails cut off.
     *
     * <p>Trimming a card name is the job: those are arbitrary text nobody chose and a panel
     * cannot be built around the longest one in Magic. Trimming a line the mod wrote is not -
     * it is a label that has stopped saying what it was written to say, and the fix is more
     * room or fewer words rather than an ellipsis. So the two are counted apart, by whether
     * the line came from the language file.
     *
     * <p>Keys rather than a count, because a count says something is wrong and a key says
     * which. Read by the scripted run at the end, which is the only place that sees the whole
     * mod drawn; a set, because a label too long is too long on every frame.
     */
    private static final java.util.Set<String> trimmedCopy =
            new java.util.LinkedHashSet<>();

    /** The keys of any of the mod's own lines that had to be cut short. */
    public static java.util.Set<String> trimmedCopy() {
        return java.util.Set.copyOf(trimmedCopy);
    }

    /**
     * Notes a line that lost its tail, if it was one of ours.
     *
     * <p>A translatable component is the mod talking; a literal is almost always a name out
     * of a card or a deck. A translatable with arguments counts as ours too - "Look: %s" that
     * does not fit is still a label nobody can read - and the key is enough to find it.
     */
    private static void noteTrim(Component text) {
        if (text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                said) {
            trimmedCopy.add(said.getKey());
        }
    }

    private static final String ELLIPSIS = "...";

    private GuiText() {
    }

    /** Draws left-aligned at {@code x}, shrinking or trimming to sit inside {@code maxWidth}. */
    public static void draw(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        drawFitted(graphics, font, text, x, y, maxWidth, color);
    }

    /** Draws centered on {@code centerX}, shrinking or trimming to sit inside {@code maxWidth}. */
    public static void drawCentered(
            GuiGraphics graphics, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        drawFitted(graphics, font, text, centerX - width(font, text, maxWidth) / 2, y, maxWidth, color);
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

    /**
     * Draws text over as many lines as it needs, each line centered, and says how tall it
     * came out.
     *
     * <p>For a whole sentence in a box that may be narrower than it - a message in the middle
     * of an empty pile, say. Shrinking a sentence to fit one line makes it unreadable and
     * trimming it makes it untrue; breaking it costs a line of height and keeps every word.
     */
    public static int drawWrappedCentered(
            GuiGraphics graphics, Font font, Component text, int centerX, int y, int maxWidth,
            int color) {
        int line = y;
        for (FormattedCharSequence row : font.split(text, Math.max(1, maxWidth))) {
            graphics.drawString(font, row, centerX - font.width(row) / 2, line, color, false);
            line += font.lineHeight + 1;
        }
        return line - y;
    }

    /** Draws text over as many lines as it needs, breaking on words. */
    public static void drawWrapped(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        int line = y;
        for (FormattedCharSequence row : font.split(text, Math.max(1, maxWidth))) {
            graphics.drawString(font, row, x, line, color, false);
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

    /** Draws centered on {@code centerX} at exactly this scale, whatever the text's own width. */
    public static void drawCenteredAt(
            GuiGraphics graphics, Font font, Component text, int centerX, int y,
            float scale, int color) {
        drawAt(graphics, font, text, centerX - font.width(text) * scale / 2f, y, scale, color);
    }

    /**
     * Draws with its right-hand end at {@code rightX}, at exactly this scale.
     *
     * <p>For a column of labels that belongs to something to one side of it. Centering each
     * one in its own box gives a column with a ragged edge against the thing it names, and
     * the shorter the word the further it sits from what it is labeling - which reads as
     * the words having been dropped in rather than laid out.
     */
    public static void drawFlushRight(
            GuiGraphics graphics, Font font, Component text, int rightX, int y,
            float scale, int color) {
        drawAt(graphics, font, text, rightX - font.width(text) * scale, y, scale, color);
    }

    /** Draws with its left-hand end at {@code leftX}, at exactly this scale. */
    public static void drawFlushLeft(
            GuiGraphics graphics, Font font, Component text, int leftX, int y,
            float scale, int color) {
        drawAt(graphics, font, text, leftX, y, scale, color);
    }

    private static void drawAt(
            GuiGraphics graphics, Font font, Component text, float x, int y,
            float scale, int color) {
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
        graphics.drawString(font, sequence, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * Draws at one size, cutting the tail off rather than shrinking to fit.
     *
     * <p>The opposite trade to {@link #draw}, and it exists for writing that sits <em>on</em>
     * something whose size the player controls. Reported from the four-player session:
     * "writing on cards should not scale with scrolling out - stay one size". Shrinking is
     * right for a label in a panel, where the panel is the size it is and the label has to
     * live in it. It is wrong for a note on a card, where zooming out shrank every note on
     * the board toward illegible while the player was still being asked to read them - and a
     * word and a half at full size beats a whole sentence nobody can make out.
     *
     * <p>Nothing at all below {@code leastWidth}, because a note trimmed to one letter and an
     * ellipsis is not information, it is a smudge on the card.
     */
    public static void drawTrimmed(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth,
            int leastWidth, int color) {
        if (maxWidth < leastWidth) {
            return;
        }
        int width = font.width(text);
        if (width == 0) {
            return;
        }
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        noteTrim(text);
        int room = maxWidth - font.width(ELLIPSIS);
        FormattedText shown = FormattedText.composite(
                font.substrByWidth(text, Math.max(0, room)), FormattedText.of(ELLIPSIS));
        graphics.drawString(font, Language.getInstance().getVisualOrder(shown), x, y, color, false);
    }

    /**
     * Works in {@link FormattedText} rather than in plain strings, so a bold or colored
     * component still arrives bold or colored after being shrunk or trimmed - flattening to
     * a string here would quietly drop the styling a caller went to the trouble of adding.
     */
    private static void drawFitted(
            GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        int width = font.width(text);
        if (width == 0) {
            return;
        }
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }

        float scale = Math.max(MINIMUM_SCALE, (float) maxWidth / width);
        FormattedText shown = text;
        if (width * scale > maxWidth) {
            // Even at the smallest readable size it does not fit, so the tail goes.
            noteTrim(text);
            int room = Math.round(maxWidth / scale) - font.width(ELLIPSIS);
            shown = FormattedText.composite(
                    font.substrByWidth(text, Math.max(0, room)), FormattedText.of(ELLIPSIS));
        }
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(shown);

        graphics.pose().pushPose();
        // Keep the shrunken line on the same baseline the full-size one would have used,
        // so a shrunk row does not sit visibly higher than its neighbors.
        graphics.pose().translate(x, y + (font.lineHeight - font.lineHeight * scale) / 2f, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, sequence, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
