package dev.gathering.client;

import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TextScale;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Drawing a card with nothing printed on it.
 *
 * <p>Blank stock is the one card in the mod whose face is not a picture somebody fetched: it
 * is what a player wrote on it, and the writing is the entire card. So it is drawn here
 * rather than by the art path - there is no art to draw - and the words fill the card instead
 * of sitting in the band across the top that an ordinary card's note uses.
 *
 * <p>Two stocks that read as two objects at a glance, which is the point of having two. Blank
 * is paper: warm, pale, ordinary, the scrap somebody tore off to remember who has the
 * monarch. An emblem is black with a gold rule and says so, because an emblem is a thing that
 * cannot be removed and a table needs to be able to tell it apart from the note somebody will
 * rub out next turn.
 *
 * <p>The writing shrinks to fit and then loses its tail, the same rule the rest of the mod's
 * text follows - see {@link GuiText}. A card on a four-player board is small, and a card
 * whose whole content is a sentence has to say as much of that sentence as it honestly can.
 *
 * <p>Client-only.
 */
public final class PaperFace {

    private static final int BLANK_INK = 0xFF2B2620;

    private static final int EMBLEM_INK = 0xFFF2E7C4;

    /** The word "Emblem" across the top of one. Text, so a color rather than a texture. */
    private static final int EMBLEM_TITLE = 0xFFC9A227;

    /** How far the scale search steps. Small enough to find a fit, coarse enough to stop. */
    private static final float STEP = 0.05f;

    private static final String ELLIPSIS = "...";

    private PaperFace() {
    }

    /** Which stock this card is, or empty for every card that is a real printing. */
    public static Optional<PaperStock> stockOf(CardView card) {
        return card instanceof CardView.Visible visible
                ? PaperStock.of(visible.identity())
                : Optional.empty();
    }

    /** Whether the writing on this card is its face rather than a note across it. */
    public static boolean isPaper(CardView card) {
        return stockOf(card).isPresent();
    }

    /**
     * The face of a card that has no art: its blank stock, or the empty frame.
     *
     * <p>One call rather than a check at each drawing site. Every place that draws a card
     * falls back to an empty frame when it has no picture yet, and a blank card has no
     * picture and never will - so the fallback is where the two meet, and putting the choice
     * here is what stops one board learning to draw an emblem and the other not.
     */
    public static void drawOrInset(
            GuiGraphics graphics, Font font, CardView card, int x, int y, int width, int height) {
        PaperStock stock = stockOf(card).orElse(null);
        if (stock == null) {
            GatheringSprites.inset(graphics, x, y, width, height);
            return;
        }
        draw(graphics, font, stock, card.writtenOn().orElse(null), x, y, width, height);
    }

    /** The same, for a box given as a rectangle. */
    public static void drawOrInset(GuiGraphics graphics, Font font, CardView card, Rect where) {
        drawOrInset(graphics, font, card, where.x(), where.y(), where.width(), where.height());
    }

    /** Blank stock with whatever is written on it, filling the box. */
    public static void draw(
            GuiGraphics graphics, Font font, PaperStock stock, String text,
            int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean emblem = stock == PaperStock.EMBLEM;
        // The stock, its edge and the inner rule that makes a rectangle read as a card, all
        // in one nine-sliced texture. Nine-slicing is what makes one file survive both sizes
        // this is drawn at: the border clamps to half the box, so the edge and the rule stay
        // the width they were painted whether the card is a fifth of the screen or a fifth
        // of a four-player board.
        GatheringSprites.draw(graphics, emblem ? Element.PAPER_EMBLEM : Element.PAPER_BLANK,
                x, y, width, height);
        // The writing starts clear of the rule the texture paints two pixels in. Wider on a
        // large card, tight on a small one: four pixels either side of a card that is
        // thirty-seven wide is the difference between a board that says "Creatures" and one
        // that says "Crea...".
        int air = width >= 60 ? 6 : 4;
        int left = x + air;
        int room = width - air * 2;
        int top = y + air;
        int floor = y + height - air;

        // The word, when there is room for it and a line of writing underneath. An emblem
        // with its title and nothing else would be a card that says less than its color did.
        //
        // And only when the whole word fits across: a card small enough to cut it short says
        // "Em..." where it meant to say what kind of card it is, which is worse than not
        // saying it - the writing underneath is the part that carries the meaning anyway.
        Component title = Component.translatable("card.gathering.emblem");
        if (emblem && floor - top >= font.lineHeight * 2 + 2 && font.width(title) <= room) {
            GuiText.drawCentered(graphics, font, title, x + width / 2, top, room, EMBLEM_TITLE);
            top += font.lineHeight + 2;
        }
        writeAcross(graphics, font, text, left, top, room, floor - top,
                emblem ? EMBLEM_INK : BLANK_INK);
    }

    /**
     * The writing, as large as the card can hold it and centered in what is left.
     *
     * <p>Shrunk before it is trimmed, because every word of a note is there on purpose: it is
     * a sentence somebody typed to tell the table something, and half of it is worse than all
     * of it small. Only when the smallest readable size still will not hold a line does that
     * line lose its tail - which is the rule the rest of the mod's text follows.
     */
    private static void writeAcross(
            GuiGraphics graphics, Font font, String text,
            int x, int y, int room, int height, int ink) {
        if (text == null || text.isBlank() || room <= 0 || height <= 0) {
            return;
        }
        float scale = TextScale.FULL;
        List<String> lines = wrap(font, text, room, scale);
        while (!fits(font, lines, room, height, scale) && scale - STEP >= TextScale.SMALLEST) {
            scale -= STEP;
            lines = wrap(font, text, room, scale);
        }
        float lineHeight = (font.lineHeight + 1) * scale;
        // As many lines as there is room for. A card too short for the whole note shows the
        // start of it rather than nothing, and the pen and the inspect panel have the rest.
        int drawable = Math.max(1, (int) (height / lineHeight));
        if (lines.size() > drawable) {
            lines = new ArrayList<>(lines.subList(0, drawable));
        }
        float line = y + Math.max(0f, (height - lines.size() * lineHeight) / 2f);
        for (String row : lines) {
            GuiText.drawCenteredAt(graphics, font, Component.literal(trimmed(font, row, room, scale)),
                    x + room / 2, Math.round(line), scale, ink);
            line += lineHeight;
        }
    }

    /** Whether every line sits inside the width and they all sit inside the height. */
    private static boolean fits(Font font, List<String> lines, int room, int height, float scale) {
        if (lines.size() * (font.lineHeight + 1) * scale > height) {
            return false;
        }
        for (String line : lines) {
            if (font.width(line) * scale > room) {
                return false;
            }
        }
        return true;
    }

    /**
     * Greedy word wrap at this scale.
     *
     * <p>Its own rather than the font's splitter because what is being wrapped is one line of
     * plain letters - a note is cleaned to exactly that - and the font wraps at a width in
     * font units, which is the wrong question once the text is being drawn at four fifths of
     * its size. A word too long for the line on its own stays on the line and is trimmed
     * later; breaking a word in half would make it a different word.
     */
    private static List<String> wrap(Font font, String text, int room, float scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() == 0 || font.width(candidate) * scale <= room) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    /** The line, or as much of it as fits with an ellipsis where the rest was. */
    private static String trimmed(Font font, String line, int room, float scale) {
        if (font.width(line) * scale <= room) {
            return line;
        }
        String shorter = line;
        while (!shorter.isEmpty() && font.width(shorter + ELLIPSIS) * scale > room) {
            shorter = shorter.substring(0, shorter.length() - 1);
        }
        return shorter.isEmpty() ? "" : shorter + ELLIPSIS;
    }
}
