package dev.gathering.server;

import dev.gathering.network.NoticePayload;
import dev.gathering.network.Sending;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The server's way of telling one player one thing, now.
 * <p>What this replaces is {@code player.displayClientMessage(line, true)}: the action bar,
 * which is the right place for an answer to a click and the wrong place for one given while a
 * screen is open, because the screen is drawn over it. Sent as a payload instead, the client
 * puts it where it can actually be read.
 * <p>Falls back to the action bar for a client that never agreed to take the mod's payloads -
 * a vanilla client on a server running this, which is a connection the mod is expected to
 * survive. That client has none of the mod's screens open either, so the action bar is exactly
 * right for it.
 * <p>Not for chat. Everything said with {@code sendSystemMessage} is a line in the log a
 * player can scroll back through, and moving those here would be replacing a record with a
 * notice that fades.
 */
public final class Notices {

    private Notices() {
    }

    /**
     * Says it to one player, if that player is a real one on this server.
     * <p>Takes a {@code Player} rather than a {@code ServerPlayer} because most of the callers
     * are block interactions, which vanilla calls on both sides. The client-side copy says
     * nothing: the server's own call is what reaches the screen.
     */
    public static void tell(Player player, Component line) {
        if (!(player instanceof ServerPlayer told) || line == null) {
            return;
        }
        if (!Sending.to(told, new NoticePayload(line))) {
            told.displayClientMessage(line, true);
        }
    }
}
