package dev.gathering.client;

import dev.gathering.core.ui.CardShape;
import dev.gathering.core.ui.Rect;
import dev.gathering.Gathering;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;

/**
 * One card, drawn: its printed face and its oracle text.
 *
 * <p>This is the only place a card is drawn for reading, in three sizes for three places -
 * beside the cursor, into a box a screen set aside, and filling the screen. They share every
 * pixel of the drawing so the card looks like itself wherever you meet it; only the geometry
 * differs. The table's inspect panel will be the cursor variant, unchanged.
 *
 * <p>Oracle text rather than printed text throughout, because errata accuracy is the reason
 * to show text at all rather than just the image.
 *
 * <p>Everything here draws from what this client already has. It never blocks and never asks
 * the server for anything, so it can run every frame.
 *
 * <p>Client-only.
 */
public final class CardInspectPanel {

    /** The printed aspect ratio, 2.5 by 3.5 inches. */

    private static final int BACKDROP = 0xC0000000;
    private static final int PLACEHOLDER = 0xE0101014;
    private static final int PLACEHOLDER_BORDER = 0xFF3A3A44;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM_TEXT = 0xFF9A9690;

    /**
     * What somebody wrote on a card, across the top of it, and the band it is written on.
     *
     * <p>Pale blue rather than the card's own ink so a note reads as somebody's handwriting
     * and not as printed text, and on a backing because a card's own name is light on dark as
     * often as it is dark on light and a note has to be readable over either.
     */
    private static final int WRITING_TEXT = 0xFFBFD8FF;

    private static final int GAP = 8;
    private static final int PADDING = 8;
    private static final int SIDEBAR_WIDTH = 180;
    private static final float FULL_SCREEN_HEIGHT_FRACTION = 0.82f;


    /**
     * How tall the art is in the cursor panel, as a share of the screen, and the bounds it
     * may not leave.
     *
     * <p>Relative because a GUI-scaled screen is anywhere between about 240 and 700 units
     * tall depending on the player's GUI scale, and a fixed size that reads as a small
     * preview on one is most of the screen on another.
     */
    // Big enough to actually read. This was cut to 0.28 of the window with a 120 ceiling to
    // stop the panel covering the zone column during a match - but a table screen shows its
    // own preview in a place chosen to keep itself legible, and {@code renderAtCursor} bows
    // out on any screen that does. So the only screens left here are inventories and chests,
    // where there is nothing behind worth protecting and a card too small to read is a
    // reading tool that does not work.
    private static final float CURSOR_ART_FRACTION = 0.55f;
    private static final int CURSOR_ART_MAX = 320;
    private static final int CURSOR_ART_MIN = 96;

    /** The art may shrink this far to give a wordy card's text somewhere to go. */
    private static final int CURSOR_ART_FLOOR = 56;

    /** Oracle text wrapped much narrower than this stops being worth reading. */
    private static final int CURSOR_TEXT_WIDTH = 190;

    /** Vanilla's own tooltip offsets, so the panel lands where the tooltip it replaces was. */
    private static final int CURSOR_OFFSET_X = 12;
    private static final int CURSOR_OFFSET_Y = -12;
    private static final int SCREEN_EDGE = 6;

    /** The most of the window's height the panel beside the cursor may take. */
    private static final float MOST_OF_THE_WINDOW = 0.8f;

    /**
     * How far in front of everything else a panel drawn over a screen sits.
     *
     * <p>Slot items are drawn at depth 150 and above, so a panel drawn afterwards at depth
     * zero still comes out behind them - the item pokes through the card you are trying to
     * read. Vanilla puts tooltips at 400 for the same reason, and this sits with them.
     */
    private static final float OVER_ITEMS = 400f;

    private CardInspectPanel() {
    }

    /**
     * The card beside the cursor, as an enclosed panel that follows it.
     *
     * <p>Positioned the way a tooltip is - down and to the right, flipping to the other side
     * near an edge - because that is the movement a player's hand already expects, and
     * because this panel is drawn over the vanilla tooltip and should sit where it sat.
     */
    public static void renderBeside(
            GuiGraphics graphics, CardSummary summary, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, OVER_ITEMS);
        try {
            drawBeside(graphics, summary, anchorX, anchorY, screenWidth, screenHeight);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawBeside(
            GuiGraphics graphics, CardSummary summary, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        // Art per printed side, text per face: a split card is one picture and two rules
        // boxes.
        List<CardFaceSummary> faces = List.of(summary.sideShown(false));
        Font font = Minecraft.getInstance().font;

        int artHeight = Mth.clamp(
                Math.round(screenHeight * CURSOR_ART_FRACTION), CURSOR_ART_MIN, CURSOR_ART_MAX);
        int artWidth = CardShape.widthFor(artHeight);
        int content = Math.max(artWidth * faces.size() + GAP * (faces.size() - 1), CURSOR_TEXT_WIDTH);

        // A narrow screen, or a double-faced card, can want more width than there is.
        int available = screenWidth - SCREEN_EDGE * 2 - PADDING * 2;
        if (content > available) {
            float scale = (float) available / content;
            artHeight = Math.max(1, Math.round(artHeight * scale));
            artWidth = CardShape.widthFor(artHeight);
            content = available;
        }

        List<Line> text = describe(font, summary.faces(), content);
        List<Line> credit = credit(font, content);
        int creditHeight = GAP + heightOf(credit);
        // Never more than this much of the window. The panel is read *while* looking at the
        // board, so one that reaches from the top of the screen to the bottom has answered
        // the question and taken away the reason for asking it.
        int ceiling = Math.min(
                screenHeight - SCREEN_EDGE * 2, Math.round(screenHeight * MOST_OF_THE_WINDOW));
        int furniture = PADDING * 2 + GAP + heightOf(text) + creditHeight;

        // A wordy card takes the room out of the art rather than off the end of the text.
        if (furniture + artHeight > ceiling) {
            artHeight = Math.max(CURSOR_ART_FLOOR, ceiling - furniture);
            artWidth = CardShape.widthFor(artHeight);
        }
        int height = Math.min(ceiling, furniture + artHeight);
        int width = content + PADDING * 2;

        int x = anchorX + CURSOR_OFFSET_X;
        if (x + width > screenWidth - SCREEN_EDGE) {
            x = anchorX - CURSOR_OFFSET_X - width;
        }
        int y = anchorY + CURSOR_OFFSET_Y;
        if (y + height > screenHeight - SCREEN_EDGE) {
            y = screenHeight - SCREEN_EDGE - height;
        }
        x = Math.max(SCREEN_EDGE, x);
        y = Math.max(SCREEN_EDGE, y);

        GatheringSprites.panel(graphics, x, y, width, height);

        int artRow = artWidth * faces.size() + GAP * (faces.size() - 1);
        int face = x + PADDING + (content - artRow) / 2;
        for (CardFaceSummary summaryFace : faces) {
            drawFace(graphics, summaryFace, face, y + PADDING, artWidth, artHeight);
            face += artWidth + GAP;
        }
        int textTop = y + PADDING + artHeight + GAP;
        int textBottom = y + height - PADDING - creditHeight;
        draw(graphics, font, text, x + PADDING, textTop, textBottom);
        draw(graphics, font, credit, x + PADDING, textBottom + GAP, y + height - PADDING);
    }

    /**
     * The printed face, or both of them, filling a box a screen has set aside.
     *
     * <p>The art keeps its proportions and is centered in whatever is left, so a box that is
     * not exactly card-shaped - or a double-faced card sharing one box - letterboxes rather
     * than stretching. A stretched card looks like a rendering bug; a centered one looks like
     * a layout.
     */
    public static void renderArt(
            GuiGraphics graphics, CardSummary summary, int x, int y, int width, int height) {
        renderArt(graphics, summary, false, x, y, width, height);
    }

    /**
     * The same, showing the side this card is currently sitting on.
     *
     * <p>One side, not all of them. A transform card drawn as both its printed sides side by
     * side comes out as two half-size cards, which is not a thing that exists - a card lies
     * on a table one way up. Which way up is the caller's, because it is state about that
     * particular card rather than about the printing.
     */
    public static void renderArt(GuiGraphics graphics, CardSummary summary, boolean flipped,
            int x, int y, int width, int height) {
        List<CardFaceSummary> faces = List.of(summary.sideShown(flipped));
        int count = 1;
        if (width <= 0 || height <= 0) {
            return;
        }

        int artWidth = (width - GAP * (count - 1)) / count;
        int artHeight = CardShape.heightFor(artWidth);
        if (artHeight > height) {
            artHeight = height;
            artWidth = CardShape.widthFor(artHeight);
        }
        if (artWidth <= 0 || artHeight <= 0) {
            return;
        }

        int row = x + (width - (artWidth * count + GAP * (count - 1))) / 2;
        int top = y + (height - artHeight) / 2;
        for (CardFaceSummary face : faces) {
            drawFace(graphics, face, row, top, artWidth, artHeight);
            row += artWidth + GAP;
        }
    }

    /**
     * Everything the card says, filling a box a screen has set aside.
     *
     * <p>No background of its own: the screen has already framed the space, and a second
     * panel inside the first is just a smaller hole.
     */
    public static void renderText(
            GuiGraphics graphics, CardSummary summary, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<Line> credit = credit(font, width);
        int creditTop = y + height - heightOf(credit);

        draw(graphics, font, describe(font, summary.faces(), width), x, y, creditTop - GAP);
        draw(graphics, font, credit, x, creditTop, y + height);
    }

    /**
     * The card as large as the screen allows, over a dimmed backdrop.
     *
     * <p>For reading a card in hand while roaming, where there is no cursor to sit beside and
     * nothing on screen worth keeping legible behind it.
     */
    public static void renderFullScreen(
            GuiGraphics graphics, CardSummary summary, int screenWidth, int screenHeight) {
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, OVER_ITEMS);
        try {
            drawFullScreen(graphics, summary, screenWidth, screenHeight);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawFullScreen(
            GuiGraphics graphics, CardSummary summary, int screenWidth, int screenHeight) {
        graphics.fill(0, 0, screenWidth, screenHeight, BACKDROP);

        List<CardFaceSummary> faces = List.of(summary.sideShown(false));
        int cardHeight = Math.round(screenHeight * FULL_SCREEN_HEIGHT_FRACTION);
        int cardWidth = CardShape.widthFor(cardHeight);

        int totalWidth = cardWidth * faces.size() + GAP * (faces.size() - 1) + GAP + SIDEBAR_WIDTH;
        // A very wide double-faced layout would run off a narrow screen; shrink to fit.
        if (totalWidth > screenWidth - GAP * 2) {
            float scale = (float) (screenWidth - GAP * 2) / totalWidth;
            cardHeight = Math.round(cardHeight * scale);
            cardWidth = Math.round(cardWidth * scale);
            totalWidth = cardWidth * faces.size() + GAP * (faces.size() - 1) + GAP + SIDEBAR_WIDTH;
        }

        int left = (screenWidth - totalWidth) / 2;
        int top = (screenHeight - cardHeight) / 2;

        for (CardFaceSummary face : faces) {
            drawFace(graphics, face, left, top, cardWidth, cardHeight);
            left += cardWidth + GAP;
        }

        drawTextPanel(graphics, summary.faces(), left, top, SIDEBAR_WIDTH, cardHeight);
    }

    private static void drawFace(GuiGraphics graphics, CardFaceSummary face, int x, int y, int width, int height) {
        Optional<String> url = face.readableImage();
        Optional<ResourceLocation> texture = url.flatMap(ClientCardImages.get()::texture);

        if (texture.isPresent()) {
            graphics.blit(texture.get(), x, y, width, height, 0f, 0f, 1, 1, 1, 1);
            return;
        }

        // No art yet. Say which, rather than showing an empty rectangle that looks broken.
        graphics.fill(x, y, x + width, y + height, PLACEHOLDER);
        graphics.renderOutline(x, y, width, height, PLACEHOLDER_BORDER);
        Font font = Minecraft.getInstance().font;
        // Fitted to the box, not drawn at whatever width the sentence happens to be. A row of
        // small cards with no art came out as four sentences overlapping each other and the
        // cards either side, which reads as the interface being broken rather than as the art
        // not having arrived.
        int room = Math.max(1, width - PADDING * 2);
        GuiText.drawCentered(graphics, font, Component.literal(face.name()),
                x + width / 2, y + PADDING, room, TEXT);

        Component message = url.isEmpty()
                ? Component.translatable("overlay.gathering.no_image")
                : ClientCardImages.get().hasFailed(url.get())
                        ? Component.translatable("overlay.gathering.image_unavailable")
                        : Component.translatable("overlay.gathering.fetching_image");
        // Only if the whole of it fits. Half a sentence in a box the size of a postage stamp
        // says less than the empty box does, and the card's own name is already there.
        int lines = GuiText.linesNeeded(font, message, room);
        int needs = lines * (font.lineHeight + 1);
        if (needs <= height - PADDING * 2 - font.lineHeight) {
            int top = y + (height - needs) / 2;
            for (var row : font.split(message, room)) {
                // Drawn as the wrapped sequence rather than turned back into a string:
                // FormattedCharSequence has no readable toString, and the one it does have
                // would have put an object identity on the card.
                graphics.drawString(font, row, x + (width - font.width(row)) / 2, top,
                        DIM_TEXT, false);
                top += font.lineHeight + 1;
            }
        }
    }

    private static void drawTextPanel(
            GuiGraphics graphics, List<CardFaceSummary> faces, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PLACEHOLDER);
        graphics.renderOutline(x, y, width, height, PLACEHOLDER_BORDER);

        Font font = Minecraft.getInstance().font;
        int textWidth = width - PADDING * 2;
        List<Line> credit = credit(font, textWidth);
        int creditTop = y + height - PADDING - heightOf(credit);

        draw(graphics, font, describe(font, faces, textWidth), x + PADDING, y + PADDING, creditTop - GAP);
        draw(graphics, font, credit, x + PADDING, creditTop, y + height - PADDING);
    }

    /**
     * Everything worth saying about a card, wrapped to a width.
     *
     * <p>Measured up front so a panel that has to size itself around the text can ask how
     * tall it will be before drawing any of it.
     */
    private static List<Line> describe(Font font, List<CardFaceSummary> faces, int width) {
        List<Line> lines = new ArrayList<>();
        for (int index = 0; index < faces.size(); index++) {
            CardFaceSummary face = faces.get(index);
            if (index > 0) {
                rule(lines);
            }
            wrap(lines, font, header(face), width, TEXT);
            wrap(lines, font, face.typeLine(), width, DIM_TEXT);
            rule(lines);
            wrap(lines, font, face.oracleText(), width, TEXT);
        }
        return lines;
    }

    /**
     * The Scryfall credit, which every panel showing card data carries.
     *
     * <p>Kept out of {@link #describe} and pinned to the bottom of the text area by each
     * caller, with its height reserved. Flowed in with the oracle text it would be the first
     * thing a wordy card pushes off the end of the panel, and the one line here that is not
     * allowed to be optional is this one.
     */
    private static List<Line> credit(Font font, int width) {
        List<Line> lines = new ArrayList<>();
        wrap(lines, font, Gathering.SCRYFALL_ATTRIBUTION, width, DIM_TEXT);
        return lines;
    }

    /** A gap, unless one is already there - two rules in a row is just a bigger hole. */
    private static void rule(List<Line> into) {
        if (!into.isEmpty() && into.get(into.size() - 1).text() != null) {
            into.add(Line.rule());
        }
    }

    private static void wrap(List<Line> into, Font font, String text, int width, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String paragraph : text.split("\n")) {
            // Through ManaText, so {T} and {1}{W} arrive as symbols - and, because they are
            // just styled characters, wrap and measure like any other text.
            for (FormattedCharSequence sequence : font.split(ManaText.of(paragraph), width)) {
                into.add(new Line(sequence, color));
            }
        }
    }

    private static int heightOf(List<Line> lines) {
        Font font = Minecraft.getInstance().font;
        int height = 0;
        for (Line line : lines) {
            height += line.height(font);
        }
        return height;
    }

    private static void draw(GuiGraphics graphics, Font font, List<Line> lines, int x, int y, int bottom) {
        int line = y;
        for (Line item : lines) {
            int height = item.height(font);
            if (line + height > bottom) {
                break;
            }
            if (item.text() != null) {
                graphics.drawString(font, item.text(), x, line, item.color(), false);
            }
            line += height;
        }
    }

    private static String header(CardFaceSummary face) {
        return face.manaCost().isEmpty() ? face.name() : face.name() + "   " + face.manaCost();
    }

    /** One drawn line, or - with no text - the gap that separates two blocks of them. */
    private record Line(FormattedCharSequence text, int color) {

        private static final int RULE_HEIGHT = 4;

        static Line rule() {
            return new Line(null, 0);
        }

        int height(Font font) {
            return text == null ? RULE_HEIGHT : font.lineHeight + 1;
        }
    }

    /**
     * Writes a card's note across the top of the art it is drawn on.
     *
     * <p>One method rather than one per screen. The felt draws notes and so does every pile
     * screen opened off it, and a note that read one way on the table and another way in a
     * graveyard would be two features wearing the same name. It was two copies until one of
     * them grew a guard about slots too short to write in and the other did not.
     *
     * <p>At the top because counters go along the bottom and the two must not fight over the
     * same band: a card with three counters and a note is a card somebody is keeping careful
     * track of, which is exactly when both have to be readable at once.
     */
    public static void drawNote(GuiGraphics graphics, Font font, String note, Rect art) {
        if (note == null || note.isBlank() || art.height() < font.lineHeight + 2) {
            return;
        }
        graphics.fill(art.x(), art.y() + 1, art.right(), art.y() + font.lineHeight + 1, BACKDROP);
        GuiText.draw(graphics, font, Component.literal(note),
                art.x() + 2, art.y() + 2, art.width() - 4, WRITING_TEXT);
    }

    /**
     * The box a written power and toughness is drawn in.
     *
     * <p>Warm rather than the cool gray the rest of the board uses, because it is the one
     * number on a card that somebody put there by hand, and the difference between "printed"
     * and "we agreed this" should be visible from across the table.
     */
    private static final int STRENGTH_BADGE = 0xE02A1B12;
    private static final int STRENGTH_EDGE = 0xFFD9A441;
    private static final int STRENGTH_TEXT = 0xFFFFE6B0;

    /**
     * The power and toughness somebody wrote on it, in the corner where the printed ones are.
     *
     * <p>Where the card already puts them, so a board reads the same whether the numbers are
     * printed or written - and here rather than in either screen, so a card in a graveyard
     * and the same card on the felt can never come to look like different features.
     *
     * <p>Nothing is worked out. What is drawn is exactly what somebody typed - see
     * {@link dev.gathering.core.game.CardStrength}, and section 16 of the brief.
     *
     * @return the line anything stacking up the card may start from, which is above this
     *     when there is something written and the card's own bottom edge when there is not
     */
    public static int drawStrength(GuiGraphics graphics, Font font, String strength, Rect art) {
        int floor = art.bottom() - 2;
        if (strength == null || strength.isBlank() || art.height() < font.lineHeight + 4) {
            return floor;
        }
        Component numbers = Component.literal(strength);
        int room = Math.max(1, art.width() - 4);
        int wide = Math.min(room, font.width(numbers) + 4);
        int high = font.lineHeight + 1;
        int left = art.right() - 2 - wide;
        int top = floor - high;
        graphics.fill(left, top, left + wide, top + high, STRENGTH_BADGE);
        graphics.renderOutline(left, top, wide, high, STRENGTH_EDGE);
        GuiText.drawCenteredAt(graphics, font, numbers, left + wide / 2, top + 1, 1f, STRENGTH_TEXT);
        return top - 1;
    }
}
