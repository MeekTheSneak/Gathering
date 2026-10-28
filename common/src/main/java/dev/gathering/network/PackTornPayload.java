package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the pack is open - hand over the cards waiting under its wrapper.
 * <p>Sent when the tear reaches the end, or when the screen closes first. The token is the one the
 * server sent with the pack; anything else tears nothing. See {@code PackWrappers}.
 */
public record PackTornPayload(String wrapper) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PackTornPayload> TYPE = GatheringPayloads.type("pack_torn");

    public static final StreamCodec<RegistryFriendlyByteBuf, PackTornPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(40), PackTornPayload::wrapper,
            PackTornPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
