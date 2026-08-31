package dev.gathering.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen that was opened from another one, and goes back to it when it closes.
 *
 * <p>Minecraft's default is to close to nothing, which for a screen you reached from an
 * inventory is right - there was a world behind it and you wanted the world. For a screen you
 * reached from the table it is a dead end: you open your graveyard, press escape, and find
 * yourself standing next to a block with your game somewhere behind it. Everything the table
 * opens is a detour and every detour comes back.
 *
 * <p>Going back to the <em>instance</em> rather than to a fresh one is deliberate. The table
 * screen holds where the view is pointing, how far it is zoomed and whether the log is up, and
 * a player who looks something up should find the table exactly as they left it.
 *
 * <p>Client-only.
 */
public abstract class ChildScreen extends Screen {

    /** Where closing goes. Null means out, which is the ordinary behavior. */
    private final Screen back;

    /**
     * Whether the screen behind is drawn, as distinct from returned to.
     *
     * <p>Two questions that were one field. Almost every detour wants the board behind it -
     * that is what {@link #renderBackground} is for - but a screen whose whole content is a
     * grid of pictures gains nothing from it and loses something to it: the mod's mana
     * symbols are a bitmap font with one texture per symbol, so each lands in a batch of its
     * own, and a batch is drawn when the frame empties it rather than when it was written.
     * The deck screen's land buttons came out on top of the sleeve picker's swatches for that
     * reason, and flushing what the parent had drawn did not bring them down. Where the parent
     * would be covered anyway, not drawing it is both the fix and the cheaper frame.
     */
    private final boolean showParent;

    protected ChildScreen(Component title, Screen back) {
        this(title, back, true);
    }

    protected ChildScreen(Component title, Screen back, boolean showParent) {
        super(title);
        this.back = back;
        this.showParent = showParent;
    }

    @Override
    public void onClose() {
        ClientHoverState.clear();
        Minecraft.getInstance().setScreen(back);
    }

    /**
     * Never. All of these are opened mid-game, and a screen that pauses a shared game because
     * one player is reading their graveyard would stop the game for everybody.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * The screen this was opened from, drawn underneath, behind a light scrim.
     *
     * <p>Every one of these is a detour taken to decide something about the board, and the
     * board was not on screen while it was being decided: what is behind a child screen is
     * whatever the game happens to be drawing, which for a table played on the window is
     * grass and sky. So the parent is drawn first. It costs one more pass over a screen that
     * is already cheap, and it means a graveyard read mid-turn is a box with the game behind
     * it rather than a room somebody has walked into.
     *
     * <p>With the cursor put where nothing is, because the parent is a picture here: a card
     * lighting up under a pointer that is really over this screen's own buttons would be the
     * board answering something nobody asked it.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (back == null || !showParent) {
            GatheringSprites.draw(graphics, GatheringSprites.Element.SCREEN_BACKDROP,
                    0, 0, this.width, this.height);
            return;
        }
        // A window resized while this was open resized this screen and not the one behind it,
        // which only finds out when it is put back. It is being drawn now, so it finds out now.
        if (back.width != this.width || back.height != this.height) {
            back.resize(Minecraft.getInstance(), this.width, this.height);
        }
        back.render(graphics, OFF_THE_SCREEN, OFF_THE_SCREEN, partialTick);
        // Everything the parent drew, put on the screen before this one draws a pixel.
        // Text goes into a batch that is drawn when somebody empties it rather than when it
        // was written, and a batch left full outlives whatever is drawn next: the deck
        // screen's mana glyphs sat on top of the sleeve picker that had covered them, because
        // they were still queued when the panel went down. A screen drawn underneath has to
        // be finished being drawn.
        graphics.flush();
        GatheringSprites.draw(graphics, GatheringSprites.Element.SCREEN_SCRIM,
                0, 0, this.width, this.height);
    }

    /** No blur either: the point of drawing the board behind is that it can be read. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    /** Far enough outside the window that nothing behind can think it is being pointed at. */
    private static final int OFF_THE_SCREEN = -1000;
}
