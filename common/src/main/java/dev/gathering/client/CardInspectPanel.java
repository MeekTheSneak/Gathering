package dev.gathering.client;

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
    private static final float CARD_ASPECT = 488f / 680f;

    private static final int BACKDROP = 0xC0000000;
    private static final int PLACEHOLDER = 0xE0101014;
    private static final int PLACEHOLDER_BORDER = 0xFF3A3A44;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM_TEXT = 0xFF9A9690;

    private static final int GAP = 8;
    private static final int PADDING = 8;
    private static final int SIDEBAR_WIDTH = 180;
    private static final float FULL_SCREEN_HEIGHT_FRACTION = 0.82f;

    /** How much of a boxed panel the art may take before the oracle text is squeezed out. */
    private static final float BOXED_ART_FRACTION = 0.6f;

    /**
     * How tall the art is in the cursor panel, as a share of the screen, and the bounds it
     * may not leave.
     *
     * <p>Relative because a GUI-scaled screen is anywhere between about 240 and 700 units
     * tall depending on the player's GUI scale, and a fixed size that reads as a small
     * preview on one is most of the screen on another.
     */
    private static final float CURSOR_ART_FRACTION = 0.45f;
    private static final int CURSOR_ART_MAX = 240;
    private static final int CURSOR_ART_MIN = 110;

    /** The art may shrink this far to give a wordy card's text somewhere to go. */
    private static final int CURSOR_ART_FLOOR = 84;

    /** Oracle text wrapped much narrower than this stops being worth reading. */
    private static final int CURSOR_TEXT_WIDTH = 170;

    /** Vanilla's own tooltip offsets, so the panel lands where the tooltip it replaces was. */
    private static final int CURSOR_OFFSET_X = 12;
    private static final int CURSOR_OFFSET_Y = -12;
    private static final int SCREEN_EDGE = 6;

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
        List<CardFaceSummary> faces = summary.faces();
        Font font = Minecraft.getInstance().font;

        int artHeight = Mth.clamp(
                Math.round(screenHeight * CURSOR_ART_FRACTION), CURSOR_ART_MIN, CURSOR_ART_MAX);
        int artWidth = Math.round(artHeight * CARD_ASPECT);
        int content = Math.max(artWidth * faces.size() + GAP * (faces.size() - 1), CURSOR_TEXT_WIDTH);

        // A narrow screen, or a double-faced card, can want more width than there is.
        int available = screenWidth - SCREEN_EDGE * 2 - PADDING * 2;
        if (content > available) {
            float scale = (float) available / content;
            artHeight = Math.max(1, Math.round(artHeight * scale));
            artWidth = Math.round(artHeight * CARD_ASPECT);
            content = available;
        }

        List<Line> text = describe(font, faces, content);
        List<Line> credit = credit(font, content);
        int creditHeight = GAP + heightOf(credit);
        int ceiling = screenHeight - SCREEN_EDGE * 2;
        int furniture = PADDING * 2 + GAP + heightOf(text) + creditHeight;

        // A wordy card takes the room out of the art rather than off the end of the text.
        if (furniture + artHeight > ceiling) {
            artHeight = Math.max(CURSOR_ART_FLOOR, ceiling - furniture);
            artWidth = Math.round(artHeight * CARD_ASPECT);
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
     * <p>The art keeps its proportions and is centred in whatever is left, so a box that is
     * not exactly card-shaped - or a double-faced card sharing one box - letterboxes rather
     * than stretching. A stretched card looks like a rendering bug; a centred one looks like
     * a layout.
     */
    public static void renderArt(
            GuiGraphics graphics, CardSummary summary, int x, int y, int width, int height) {
        List<CardFaceSummary> faces = summary.faces();
        int count = Math.max(1, faces.size());
        if (width <= 0 || height <= 0) {
            return;
        }

        int artWidth = (width - GAP * (count - 1)) / count;
        int artHeight = Math.round(artWidth / CARD_ASPECT);
        if (artHeight > height) {
            artHeight = height;
            artWidth = Math.round(artHeight * CARD_ASPECT);
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

        List<CardFaceSummary> faces = summary.faces();
        int cardHeight = Math.round(screenHeight * FULL_SCREEN_HEIGHT_FRACTION);
        int cardWidth = Math.round(cardHeight * CARD_ASPECT);

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

        drawTextPanel(graphics, faces, left, top, SIDEBAR_WIDTH, cardHeight);
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
        Component message = url.isEmpty()
                ? Component.translatable("overlay.gathering.no_image")
                : ClientCardImages.get().hasFailed(url.get())
                        ? Component.translatable("overlay.gathering.image_unavailable")
                        : Component.translatable("overlay.gathering.fetching_image");
        graphics.drawCenteredString(font, message, x + width / 2, y + height / 2 - 4, DIM_TEXT);
        graphics.drawCenteredString(font, Component.literal(face.name()), x + width / 2, y + 8, TEXT);
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

    private static void wrap(List<Line> into, Font font, String text, int width, int colour) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String paragraph : text.split("\n")) {
            // Through ManaText, so {T} and {1}{W} arrive as symbols - and, because they are
            // just styled characters, wrap and measure like any other text.
            for (FormattedCharSequence sequence : font.split(ManaText.of(paragraph), width)) {
                into.add(new Line(sequence, colour));
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
                graphics.drawString(font, item.text(), x, line, item.colour(), false);
            }
            line += height;
        }
    }

    private static String header(CardFaceSummary face) {
        return face.manaCost().isEmpty() ? face.name() : face.name() + "   " + face.manaCost();
    }

    /** One drawn line, or - with no text - the gap that separates two blocks of them. */
    private record Line(FormattedCharSequence text, int colour) {

        private static final int RULE_HEIGHT = 4;

        static Line rule() {
            return new Line(null, 0);
        }

        int height(Font font) {
            return text == null ? RULE_HEIGHT : font.lineHeight + 1;
        }
    }
}
