package dev.gathering.client;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The universal reading tool: hold a key over a card and see the printed face full size,
 * with its oracle text beside it.
 *
 * <p>Reading a card is the single most common thing anyone does with a card game, so this
 * has to be instant and it has to work everywhere a card can appear - in an inventory slot,
 * in your hand, and later on the table. It renders from what this client has already been
 * told and already fetched; it never blocks and never asks the server for anything.
 *
 * <p>Double-faced cards show both halves side by side, because the errata-accurate answer
 * to "what does this do" is on whichever face you were not looking at.
 *
 * <p>Client-only.
 */
public final class CardZoomOverlay {

    /** The printed aspect ratio, 2.5 by 3.5 inches. */
    private static final float CARD_ASPECT = 488f / 680f;

    private static final float HEIGHT_FRACTION = 0.82f;
    private static final int BACKDROP = 0xC0000000;
    private static final int PANEL = 0xE0101014;
    private static final int PANEL_BORDER = 0xFF3A3A44;
    private static final int TEXT = 0xFFE8E4DC;
    private static final int DIM_TEXT = 0xFF9A9690;
    private static final int GAP = 8;
    private static final int PADDING = 8;
    private static final int SIDEBAR_WIDTH = 180;

    /** How much of a side panel the art may take before the oracle text is squeezed out. */
    private static final float PANEL_ART_FRACTION = 0.6f;

    private static volatile BooleanSupplier keyHeld = () -> false;

    private CardZoomOverlay() {
    }

    /** Bound at client init to whichever key mapping the loader registered. */
    public static void bindKeyState(BooleanSupplier held) {
        keyHeld = held;
    }

    public static boolean isActive() {
        return keyHeld.getAsBoolean();
    }

    /**
     * Draws the overlay if the key is held and a card is under the cursor or in hand.
     *
     * <p>Safe to call every frame from any GUI or HUD render hook; it returns immediately
     * when there is nothing to show.
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!isActive()) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof CardPreviewHost) {
            // That screen shows the card itself, in a place chosen to leave its own content
            // readable. Drawing over the top of it would undo exactly that.
            return;
        }
        Optional<CardSummary> target = cardUnderAttention();
        if (target.isEmpty()) {
            return;
        }
        draw(graphics, screenWidth, screenHeight, target.get());
    }

    /**
     * What the player is asking about: the card in the slot under the mouse if a container
     * screen is open, otherwise the one in their hand.
     */
    static Optional<CardSummary> cardUnderAttention() {
        Minecraft minecraft = Minecraft.getInstance();

        Optional<CardSummary> hovered = summaryOf(ClientHoverState.hovered());
        if (hovered.isPresent()) {
            return hovered;
        }

        if (minecraft.screen instanceof AbstractContainerScreen<?> && minecraft.player != null) {
            // A card being dragged is held by the cursor rather than sitting in a slot.
            Optional<CardSummary> carried = summaryOf(minecraft.player.containerMenu.getCarried());
            if (carried.isPresent()) {
                return carried;
            }
        }

        Player player = minecraft.player;
        if (player == null) {
            return Optional.empty();
        }
        Optional<CardSummary> mainHand = summaryOf(player.getMainHandItem());
        return mainHand.isPresent() ? mainHand : summaryOf(player.getOffhandItem());
    }

    private static Optional<CardSummary> summaryOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<CardComponent> card = CardItem.cardOf(stack);
        return card.flatMap(ClientCardCache.get()::summary);
    }

    private static void draw(GuiGraphics graphics, int screenWidth, int screenHeight, CardSummary summary) {
        graphics.fill(0, 0, screenWidth, screenHeight, BACKDROP);

        List<CardFaceSummary> faces = summary.faces();
        int cardHeight = Math.round(screenHeight * HEIGHT_FRACTION);
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

    /**
     * The same card, drawn into a box a screen has set aside for it rather than over
     * everything.
     *
     * <p>Art across the top, oracle text beneath - a portrait layout, because a panel beside
     * a list is tall and narrow where the full-screen overlay is wide.
     */
    public static void renderInto(
            GuiGraphics graphics, CardSummary summary, int x, int y, int width, int height) {
        List<CardFaceSummary> faces = summary.faces();
        int count = Math.max(1, faces.size());

        int artWidth = (width - GAP * (count - 1)) / count;
        int artHeight = Math.round(artWidth / CARD_ASPECT);
        int artCeiling = Math.round(height * PANEL_ART_FRACTION);
        if (artHeight > artCeiling) {
            artHeight = artCeiling;
            artWidth = Math.round(artHeight * CARD_ASPECT);
        }
        if (artWidth <= 0 || artHeight <= 0) {
            return;
        }

        int row = x + (width - (artWidth * count + GAP * (count - 1))) / 2;
        for (CardFaceSummary face : faces) {
            drawFace(graphics, face, row, y, artWidth, artHeight);
            row += artWidth + GAP;
        }

        int textTop = y + artHeight + GAP;
        if (textTop < y + height) {
            drawTextPanel(graphics, faces, x, textTop, width, y + height - textTop);
        }
    }

    private static void drawFace(GuiGraphics graphics, CardFaceSummary face, int x, int y, int width, int height) {
        Optional<String> url = face.readableImage();
        Optional<net.minecraft.resources.ResourceLocation> texture =
                url.flatMap(ClientCardImages.get()::texture);

        if (texture.isPresent()) {
            graphics.blit(texture.get(), x, y, width, height, 0f, 0f, 1, 1, 1, 1);
            return;
        }

        // No art yet. Say which, rather than showing an empty rectangle that looks broken.
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, PANEL_BORDER);
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
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, PANEL_BORDER);

        Font font = Minecraft.getInstance().font;
        int textWidth = width - PADDING * 2;
        int line = y + PADDING;
        int lineHeight = font.lineHeight + 1;
        int bottom = y + height - PADDING;

        for (int index = 0; index < faces.size(); index++) {
            CardFaceSummary face = faces.get(index);
            if (index > 0) {
                line += lineHeight / 2;
                if (line < bottom) {
                    graphics.fill(x + PADDING, line, x + width - PADDING, line + 1, PANEL_BORDER);
                }
                line += lineHeight / 2;
            }

            line = drawWrapped(graphics, font, header(face), textWidth, x + PADDING, line, bottom, TEXT);
            line = drawWrapped(graphics, font, face.typeLine(), textWidth, x + PADDING, line, bottom, DIM_TEXT);
            line += 3;
            // Oracle text rather than printed text: errata accuracy is the entire point of
            // having a sidebar instead of just showing the card image.
            line = drawWrapped(graphics, font, face.oracleText(), textWidth, x + PADDING, line, bottom, TEXT);
        }

        String attribution = Gathering.SCRYFALL_ATTRIBUTION;
        graphics.drawString(font, attribution, x + PADDING, y + height - font.lineHeight - 2, DIM_TEXT, false);
    }

    private static String header(CardFaceSummary face) {
        return face.manaCost().isEmpty() ? face.name() : face.name() + "   " + face.manaCost();
    }

    private static int drawWrapped(
            GuiGraphics graphics, Font font, String text, int width, int x, int y, int bottom, int colour) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            lines.addAll(font.split(Component.literal(paragraph), width));
        }
        int line = y;
        for (FormattedCharSequence sequence : lines) {
            if (line + font.lineHeight > bottom) {
                break;
            }
            graphics.drawString(font, sequence, x, line, colour, false);
            line += font.lineHeight + 1;
        }
        return line;
    }
}
