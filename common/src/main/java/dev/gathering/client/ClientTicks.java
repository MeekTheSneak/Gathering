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
        ClientCardRequests.tick();
        // Settings are written from here rather than from the setter, so dragging a slider is
        // one write when the player lets go instead of one per frame while they choose.
        ClientSettings.tick();
        // And the row of token names, on the same debounce and for the same reason: making
        // five Treasures in a row is one write rather than five.
        RecentThings.tick();
        // The scripted run. It does nothing at all unless its property is set, and it is the
        // only thing that tells us whether a loader plays the game rather than merely
        // starting it - so it runs on both.
        DevScene.tick(client);
    }
}
