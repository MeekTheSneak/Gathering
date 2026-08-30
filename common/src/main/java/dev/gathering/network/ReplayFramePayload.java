package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one moment of a finished game.
 *
 * <p>The bytes are a {@code GameView} exactly as a live board's are, so the screen that draws
 * a game draws a replay without knowing the difference. What is <em>not</em> in here is the
 * log's events, the session seed, or anything else a client could fold a different frame out
 * of: the server holds the game and hands out pictures of it. Scrubbing asks again.
 */
public record ReplayFramePayload(String id, int step, int steps, byte[] view)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReplayFramePayload> TYPE =
            GatheringPayloads.type("replay_frame");

    public static final StreamCodec<RegistryFriendlyByteBuf, ReplayFramePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(WatchReplayPayload.MAX_ID), ReplayFramePayload::id,
                    ByteBufCodecs.VAR_INT, ReplayFramePayload::step,
                    ByteBufCodecs.VAR_INT, ReplayFramePayload::steps,
                    ByteBufCodecs.byteArray(TableViewPayload.MAX_BYTES), ReplayFramePayload::view,
                    ReplayFramePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
