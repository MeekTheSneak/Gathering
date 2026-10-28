package dev.gathering.client;

import net.minecraft.client.Minecraft;

/**
 * One client tick, written once.
 * <p>Both loaders had the same four lines in their own tick handler, and a fifth thing that
 * needs a tick would have been added to one of them. That is the same failure {@link
 * dev.gathering.server.ServerState} exists to prevent on the other side: a list kept in two
 * files is a list where an entry gets added to one of them.
 * <p>So the loaders' handlers are one call each and everything that wants a client tick is
 * named here.
 * <p>Client thread only.
 */
public final class ClientTicks {

    private ClientTicks() {
    }

    public static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        // No screen means no slots, so nothing is hovered.
        if (client.screen == null) {
            ClientHoverState.clear();
        }
        // Before anything reads a pointer, because what the pointers are measured against is
        // this count: a frame that read one on the tick that incremented it would interpolate
        // from the future.
        ClientTablePointing.tick();
        TablePointSender.tick(client);
        ClientCardRequests.tick();
        // Whose the window is, and whether a card is being read: the first decides
        // whether a view is written down or put back, the second holds one still.
        ViewKeeper.tick(client);
        // Settings are written from here rather than from the setter, so dragging a slider is
        // one write when the player lets go instead of one per frame while they choose.
        ClientSettings.tick();
        // And the row of token names, on the same debounce and for the same reason: making
        // five Treasures in a row is one write rather than five.
        RecentThings.tick();
        // Not the scripted run. That is development code and is not in the shipped mod: each
        // loader's development source set ticks it from its own hook - DevSceneTicks in both -
        // so nothing here names it and a release jar has nothing to name. It was called from
        // here until it was, which is what kept a ten-thousand-line test driver in every copy.
    }

    /**
     * The game is closing: whatever is still waiting for its tick is written now.
     * <p>Both files are written a second after the last change, and quitting leaves no second. A
     * setting changed just before closing the game was lost, found when the scripted accessibility
     * check put the sizes back as its last act and the file still held the ones it had set. Each
     * loader calls this from its own shutdown hook.
     */
    public static void stopping() {
        ClientSettings.flush();
        RecentThings.flush();
    }
}
