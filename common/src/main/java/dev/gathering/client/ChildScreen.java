package dev.gathering.client;

import net.minecraft.client.Minecraft;
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
}
