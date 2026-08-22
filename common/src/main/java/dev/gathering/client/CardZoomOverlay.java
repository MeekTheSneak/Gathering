package dev.gathering.client;

import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardSummary;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The universal reading tool: hold a key over a card and see the printed face with its
 * oracle text.
 *
 * <p>Reading a card is the single most common thing anyone does with a card game, so this
 * has to be instant and it has to work everywhere a card can appear - in an inventory slot,
 * in your hand, and later on the table. It renders from what this client has already been
 * told and already fetched; it never blocks and never asks the server for anything.
 *
 * <p>This class decides <em>whether</em> and <em>what</em>; {@link CardInspectPanel} does the
 * drawing. Inside a screen the panel follows the cursor, because there is a cursor to follow
 * and a screen underneath worth keeping legible. Over the HUD there is neither, so the card
 * takes the whole screen.
 *
 * <p>Client-only.
 */
public final class CardZoomOverlay {

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
     * Draws the full-screen read if the key is held and a card is in hand.
     *
     * <p>For the HUD, where there is no cursor. Safe to call every frame; it returns
     * immediately when there is nothing to show.
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!isActive()) {
            return;
        }
        cardInHand().ifPresent(card ->
                CardInspectPanel.renderFullScreen(graphics, card, screenWidth, screenHeight));
    }

    /**
     * Draws the card beside the cursor if the key is held and a card is under it.
     *
     * <p>For an open screen. The panel is small and sits where the tooltip would, so the
     * inventory stays readable behind it and the cursor keeps its meaning - this is the same
     * panel the table will use to inspect a card in play.
     */
    public static void renderAtCursor(
            GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isActive()) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof CardPreviewHost) {
            // That screen shows the card itself, in a place chosen to leave its own content
            // readable. A second copy chasing the cursor would undo exactly that.
            return;
        }
        cardUnderCursor().ifPresent(card ->
                CardInspectPanel.renderBeside(graphics, card, mouseX, mouseY, screenWidth, screenHeight));
    }

    /**
     * Whether the vanilla tooltip for this stack should stand down.
     *
     * <p>The inspect panel sits exactly where the tooltip sits and says everything it says.
     * Drawing both means a tooltip peeking out from behind a panel whenever its longest line
     * is wider - which the Scryfall attribution line reliably is. So the panel replaces the
     * tooltip rather than covering it.
     *
     * <p>Only for a card this client can actually draw. A card whose metadata has not arrived
     * yet keeps its tooltip, because the alternative is a stack that says nothing at all.
     */
    public static boolean replacesTooltipFor(ItemStack stack) {
        if (!isActive() || Minecraft.getInstance().screen instanceof CardPreviewHost) {
            return false;
        }
        return summaryOf(stack).isPresent();
    }

    /**
     * The card the cursor is pointing at: the one in the slot under it, or the one it is
     * carrying.
     *
     * <p>Deliberately never falls back to the card in the player's hand. Inside a screen the
     * cursor is what the player is pointing with, so holding the key over an empty slot must
     * show nothing - falling back meant a card in your hand shadowed every slot you were not
     * over, and answered a question nobody asked.
     */
    static Optional<CardSummary> cardUnderCursor() {
        Minecraft minecraft = Minecraft.getInstance();

        Optional<CardSummary> hovered = summaryOf(ClientHoverState.hovered());
        if (hovered.isPresent()) {
            return hovered;
        }
        if (minecraft.screen instanceof AbstractContainerScreen<?> && minecraft.player != null) {
            // A card being dragged is held by the cursor rather than sitting in a slot.
            return summaryOf(minecraft.player.containerMenu.getCarried());
        }
        return Optional.empty();
    }

    /** The card the player is actually holding, which is the question the HUD answers. */
    static Optional<CardSummary> cardInHand() {
        Player player = Minecraft.getInstance().player;
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
}
