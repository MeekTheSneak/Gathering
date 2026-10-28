package dev.gathering.network;

import dev.gathering.item.CardComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: tell me about this card in that collection - how many copies, and where the ones
 * with a history have been.
 * <p>Asks; the server answers only for a collection this player is standing at.
 */
public record CollectionCardAskPayload(BlockPos where, CardComponent card) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionCardAskPayload> TYPE =
            GatheringPayloads.type("collection_card_ask");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionCardAskPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionCardAskPayload::where,
                    CardComponent.STREAM_CODEC, CollectionCardAskPayload::card,
                    CollectionCardAskPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
