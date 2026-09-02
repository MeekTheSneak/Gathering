package dev.gathering.client;

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
 * <p>Reading a card is the single most common thing anyone does with a card game, so this
 * has to be instant and it has to work everywhere a card can appear - in an inventory slot,
 * in your hand, and later on the table. It renders from what this client has already been
 * told and already fetched; it never blocks and never asks the server for anything.
 * <p>This class decides <em>whether</em> and <em>what</em>; {@link CardInspectPanel} does the
 * drawing. Inside a screen the panel follows the cursor, because there is a cursor to follow
 * and a screen underneath worth keeping legible. Over the HUD there is neither, so the card
 * takes the whole screen.
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
     * <p>For the HUD, where there is no cursor. Safe to call every frame; it returns
     * immediately when there is nothing to show.
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!isActive()) {
            // Flattened between reads, so the next card comes up square rather than at
            // whatever angle the last one was left at. See CardTilt.
            CardTilt.forget();
            return;
        }
        Held held = cardInHand().orElse(null);
        if (held == null) {
            return;
        }
        // Out here the mouse is the camera, so turning your head is what turns the card. It
        // is the same gesture the cursor makes over a screen, doing the same thing.
        CardTilt.withTheHead(Minecraft.getInstance().player);
        CardInspectPanel.renderFullScreen(graphics, held.summary(), held.foil(), held.flipped(),
                held.story(), held.strength(), screenWidth, screenHeight);
    }

    /**
     * Draws the card beside the cursor if the key is held and a card is under it.
     * <p>For an open screen. The panel is small and sits where the tooltip would, so the
     * inventory stays readable behind it and the cursor keeps its meaning - this is the same
     * panel the table will use to inspect a card in play.
     */
    public static void renderAtCursor(
            GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isActive()) {
            // Flattened here as well as in the world, so a card picked up next is square
            // however the last read ended. Only one of the two hooks runs at a time - the
            // HUD one bows out whenever a screen is open - so whichever it was, one of them
            // has put the tilt back.
            CardTilt.forget();
            return;
        }
        if (Minecraft.getInstance().screen instanceof CardPreviewHost) {
            // That screen shows the card itself, in a place chosen to leave its own content
            // readable. A second copy chasing the cursor would undo exactly that.
            return;
        }
        Held under = cardUnderCursor().orElse(null);
        if (under == null) {
            return;
        }
        // The panel already follows the cursor, so the cursor's place across the window is
        // what moves the shine. A card that also turned would be two answers to one hand.
        CardTilt.toward(mouseX, mouseY, screenWidth / 2, screenHeight / 2, screenWidth, screenHeight);
        CardInspectPanel.renderBeside(graphics, under.summary(), under.foil(), under.story(),
                under.strength(), mouseX, mouseY, screenWidth, screenHeight);
    }

    /**
     * Whether the vanilla tooltip for this stack should stand down.
     * <p>The inspect panel sits exactly where the tooltip sits and says everything it says.
     * Drawing both means a tooltip peeking out from behind a panel whenever its longest line
     * is wider - which the Scryfall attribution line reliably is. So the panel replaces the
     * tooltip rather than covering it.
     * <p>Only for a card this client can actually draw. A card whose metadata has not arrived
     * yet keeps its tooltip, because the alternative is a stack that says nothing at all.
     */
    public static boolean replacesTooltipFor(ItemStack stack) {
        if (!isActive() || Minecraft.getInstance().screen instanceof CardPreviewHost) {
            return false;
        }
        return heldAs(stack).isPresent();
    }

    /**
     * The card the cursor is pointing at: the one in the slot under it, or the one it is
     * carrying.
     * <p>Deliberately never falls back to the card in the player's hand. Inside a screen the
     * cursor is what the player is pointing with, so holding the key over an empty slot must
     * show nothing - falling back meant a card in your hand shadowed every slot you were not
     * over, and answered a question nobody asked.
     */
    static Optional<Held> cardUnderCursor() {
        Minecraft minecraft = Minecraft.getInstance();

        Optional<Held> hovered = heldAs(ClientHoverState.hovered());
        if (hovered.isPresent()) {
            return hovered;
        }
        if (minecraft.screen instanceof AbstractContainerScreen<?> && minecraft.player != null) {
            // A card being dragged is held by the cursor rather than sitting in a slot.
            return heldAs(minecraft.player.containerMenu.getCarried());
        }
        return Optional.empty();
    }

    /**
     * A card this client can draw, and the two things about this particular copy of it.
     * <p>Foil and which side is up are facts about the card in somebody's hand, not about the
     * printing - two players can hold the same printing and only one of them holds a foil - so
     * they travel beside the metadata rather than inside it.
     */
    record Held(CardSummary summary, boolean foil, boolean flipped,
            dev.gathering.core.story.CardStory story, String strength) {
    }

    /** The card the player is actually holding, which is the question the HUD answers. */
    static Optional<Held> cardInHand() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return Optional.empty();
        }
        Optional<Held> mainHand = heldAs(player.getMainHandItem());
        return mainHand.isPresent() ? mainHand : heldAs(player.getOffhandItem());
    }

    /** The same stack, read as a card this client knows enough about to draw. */
    private static Optional<Held> heldAs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return CardItem.cardOf(stack).flatMap(card -> ClientCardCache.get().summary(card)
                .map(summary -> new Held(summary, card.foil(), card.flipped(),
                        dev.gathering.item.StoryComponent.on(stack),
                        // Only the table ever writes one, and only onto the card the cursor
                        // is on - so a stack read anywhere else carries none, which is what a
                        // card sitting in a box has.
                        stack == ClientHoverState.hovered()
                                ? ClientHoverState.writtenStrength()
                                : "")));
    }
}
