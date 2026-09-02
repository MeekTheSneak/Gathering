package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: show me a replay, or show me the list of them.
 * <p>An empty id asks for the list. Anything else asks for one frame of one game, and the
 * server matches the id against the names of the files it actually has rather than resolving
 * it as a path - a client cannot name a file the server did not offer it, whatever it sends.
 */
public record WatchReplayPayload(String id, int step) implements CustomPacketPayload {

    /** Long enough for a timestamp and a hash, short enough that nothing else fits. */
    public static final int MAX_ID = 64;

    public static final CustomPacketPayload.Type<WatchReplayPayload> TYPE =
            GatheringPayloads.type("watch_replay");

    public static final StreamCodec<RegistryFriendlyByteBuf, WatchReplayPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_ID), WatchReplayPayload::id,
                    ByteBufCodecs.VAR_INT, WatchReplayPayload::step,
                    WatchReplayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
