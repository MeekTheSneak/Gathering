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

    /** Where closing goes. Null means out, which is the ordinary behaviour. */
    private final Screen back;

    protected ChildScreen(Component title, Screen back) {
        super(title);
        this.back = back;
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
        if (back == null) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // A window resized while this was open resized this screen and not the one behind it,
        // which only finds out when it is put back. It is being drawn now, so it finds out now.
        if (back.width != this.width || back.height != this.height) {
            back.resize(Minecraft.getInstance(), this.width, this.height);
        }
        back.render(graphics, OFF_THE_SCREEN, OFF_THE_SCREEN, partialTick);
        graphics.fill(0, 0, this.width, this.height, SCRIM);
    }

    /** No blur either: the point of drawing the board behind is that it can be read. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    /** Far enough outside the window that nothing behind can think it is being pointed at. */
    private static final int OFF_THE_SCREEN = -1000;

    /** Enough to push the board back behind this screen, not enough to hide it. */
    private static final int SCRIM = 0x80101418;
}
