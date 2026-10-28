package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: who is let into this collection?
 * <p>Asked rather than sent with the collection, because the answer is names and only its owner
 * ever sees it: a list of who may open a cabinet is a list of who to ask for the key.
 */
public record CollectionKeysAskPayload(BlockPos where) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionKeysAskPayload> TYPE =
            GatheringPayloads.type("collection_keys_ask");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionKeysAskPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionKeysAskPayload::where,
                    CollectionKeysAskPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
