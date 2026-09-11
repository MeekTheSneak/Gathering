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

    /**
     * What the read key is called, asked of the loader that registered it.
     * <p>Bound the same way {@link #keyHeld} is, and for the same reason: which key a mapping
     * currently sits on is the one thing this cannot answer on its own. Anything that tells a
     * player how to read a card asks here rather than writing "Alt", which stops being true
     * the moment they rebind it.
     */
    private static volatile java.util.function.Supplier<net.minecraft.network.chat.Component>
            keyName = () -> net.minecraft.network.chat.Component.translatable(
                    "screen.gathering.table.key_unbound");

    private CardZoomOverlay() {
    }

    /** Bound at client init to whichever key mapping the loader registered. */
    public static void bindKeyState(BooleanSupplier held) {
        keyHeld = held;
    }

    /** Bound at client init to the same mapping's current name. */
    public static void bindKeyName(
            java.util.function.Supplier<net.minecraft.network.chat.Component> named) {
        if (named != null) {
            keyName = named;
        }
    }

    /** What to call the read key when telling somebody how to read a card. */
    public static net.minecraft.network.chat.Component keyName() {
        return keyName.get();
    }

    /**
     * Whether the key was down last time anybody asked, so a press can be told from a hold.
     * <p>There is no key event to listen for here: the read key is polled, because it is a
     * key that is <em>held</em> rather than pressed and the whole overlay is drawn from that
     * one boolean. Toggling therefore has to notice the edge itself.
     */
    private static boolean wasDown;

    /** Whether a press has left the card up, in the mode where a press is what does that. */
    private static boolean latched;

    /**
     * Whether a card should be shown full size right now.
     * <p>Two ways to mean yes, and the player chooses which. Holding is the default because it
     * is the gesture the table is built around - look, let go, carry on - and it is also the
     * one that cannot be left switched on by accident. Pressing is for anybody who cannot
     * comfortably hold a key down while moving a mouse, which is the whole of why the setting
     * exists and is not a preference about taste.
     * <p>Asked every frame by the renderer, so the edge is noticed here rather than anywhere
     * that would need a second place to keep this state.
     */
    public static boolean isActive() {
        boolean down = keyHeld.getAsBoolean();
        if (ClientSettings.holdToInspect()) {
            // Nothing latched can survive a switch back to holding, or the card would be
            // stuck up with no key to let go of.
            latched = false;
            wasDown = down;
            return down;
        }
        if (down && !wasDown) {
            latched = !latched;
        }
        wasDown = down;
        return latched;
    }

    /**
     * Puts the card down, for a server changing underneath it.
     * <p>Named {@code clear} because that is the name {@code tools/statecheck.py} looks for: a
     * client holder called this has to be named in {@link ClientState}. A card left latched up
     * across a disconnect would be a full-screen card over the main menu with no key to let go
     * of, since the key that latched it belongs to a table that is gone.
     */
    public static void clear() {
        latched = false;
        wasDown = false;
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
        CardInspectPanel.renderBeside(graphics, under.summary(), under.foil(), under.flipped(),
                under.story(), under.strength(), mouseX, mouseY, screenWidth, screenHeight);
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
