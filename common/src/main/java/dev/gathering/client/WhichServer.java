package dev.gathering.client;

import net.minecraft.client.Minecraft;

/**
 * What to call the server this client is on.
 * <p>Its own class because it is the one line of this that has to touch {@link Minecraft}, and
 * the thing that wants it - {@link RecentThings} - must not: that lives in {@code :common},
 * which is loaded on a dedicated server too, and a client class reached from there throws at
 * load rather than at use.
 * <p>A server's address when there is one, and the save's own name in single player. Neither
 * is a promise about identity; both are stable across restarts, which is the property the
 * thing asking actually needs.
 * <p>Client-only.
 */
public final class WhichServer {

    private WhichServer() {
    }

    public static String name() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return "";
        }
        net.minecraft.client.multiplayer.ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip;
        }
        net.minecraft.client.server.IntegratedServer local = client.getSingleplayerServer();
        return local == null ? "" : "local:" + local.getWorldData().getLevelName();
    }
}
