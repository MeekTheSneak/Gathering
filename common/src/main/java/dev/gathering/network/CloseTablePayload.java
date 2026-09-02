package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the game you were watching is over, so stop watching it.
 * <p>Without this a player whose table's session ended sits looking at the last board it
 * ever sent, which is worse than an empty screen because it looks live.
 */
public record CloseTablePayload() implements CustomPacketPayload {

    public static final CloseTablePayload INSTANCE = new CloseTablePayload();

    public static final CustomPacketPayload.Type<CloseTablePayload> TYPE =
            GatheringPayloads.type("close_table");

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseTablePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
