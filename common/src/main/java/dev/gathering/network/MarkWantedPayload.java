package dev.gathering.network;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: put this card on my wants list, or take it off.
 *
 * <p>A printing rather than a name, which is also what makes this safe to accept from anywhere:
 * it names a card by its Scryfall id and the server writes that id into a file. Nothing is
 * looked up, nothing is granted, and a card nobody has ever heard of is a line in a list.
 */
public record MarkWantedPayload(UUID printing, boolean wanted) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarkWantedPayload> TYPE =
            GatheringPayloads.type("mark_wanted");

    public static final StreamCodec<RegistryFriendlyByteBuf, MarkWantedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, MarkWantedPayload::printing,
                    ByteBufCodecs.BOOL, MarkWantedPayload::wanted,
                    MarkWantedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
