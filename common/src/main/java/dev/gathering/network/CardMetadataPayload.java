package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: display metadata for cards this client is entitled to see.
 *
 * <p>The one channel by which a client learns what a card is. Nothing is sent
 * speculatively: the server sends a summary when, and only when, the visibility rules put
 * that card in this client's view. That is the whole security property, and it is why a
 * spectating client is incapable of leaking a hand even if modified - it was never told.
 */
public record CardMetadataPayload(List<CardSummary> cards) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardMetadataPayload> TYPE =
            GatheringPayloads.type("card_metadata");

    public static final StreamCodec<RegistryFriendlyByteBuf, CardMetadataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    CardSummary.LIST_STREAM_CODEC, CardMetadataPayload::cards,
                    CardMetadataPayload::new);

    public CardMetadataPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
