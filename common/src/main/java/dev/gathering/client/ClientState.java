package dev.gathering.client;

/**
 * Everything this client learned from a server, forgotten in one place.
 *
 * <p>The rule is one sentence - what one server told us is not true of the next one - and it
 * used to be written twice, once per loader, as a list of holders to clear. A list written
 * twice is a list that drifts: three of these were added over time and only ever cleared by
 * whichever loader the author happened to be looking at, so a client that left one server and
 * joined another carried a table's pings, its card flights and its unread log across.
 *
 * <p>So the list lives here, the loaders call one method, and {@code tools/statecheck.py}
 * fails the build if a holder in this package grows a {@code clear} nobody added to it.
 *
 * <p>Client-only, and called on the client thread as a player disconnects.
 */
public final class ClientState {

    private ClientState() {
    }

    /** Drops everything the server we are leaving told us. */
    public static void forgetTheServer() {
        ClientCardCache.get().clear();
        ClientCardFlights.clear();
        ClientCardRequests.clear();
        ClientHoverState.clear();
        ClientTableChat.clear();
        ClientTableHighlight.clear();
        ClientTableNews.clear();
        ClientTableState.clear();
        ClientWants.clear();
        // Not a clear, because a replay is something this client is doing rather than
        // something it was told: stopping it is what closes the watch as well as dropping the
        // frame. A frame is a board with hands in it, so it is the one piece of this that
        // would be a leak rather than a smudge if it outlived the server that sent it.
        ClientReplay.stop();
    }
}
