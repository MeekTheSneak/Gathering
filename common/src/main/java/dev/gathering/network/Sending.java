package dev.gathering.network;

import dev.gathering.platform.Platform;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one way this mod puts a payload on the wire.
 *
 * <p>Because not every client can take one. A connection negotiates which channels it speaks
 * when it opens, and sending down a channel the other end never agreed to is not a dropped
 * packet - it throws, on whatever thread was sending, which for the table means a block entity
 * that takes the whole server down while it is ticking.
 *
 * <p>Which is not hypothetical. It is what happens to a game test's stand-in player, and it is
 * what would happen to anybody connected in a way this mod did not expect. A client that cannot
 * take a payload is a client that does not see whatever it was about, and that is the whole of
 * what should follow.
 *
 * <p>Every send goes through here so there is one place that is true.
 */
public final class Sending {

    private Sending() {
    }

    /**
     * Sends one payload to one player, if that player can take it.
     *
     * @return whether it went
     */
    public static boolean to(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || payload == null || player.hasDisconnected()) {
            return false;
        }
        if (!Platform.get().canReceive(player, payload.type().id())) {
            return false;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
        return true;
    }
}
