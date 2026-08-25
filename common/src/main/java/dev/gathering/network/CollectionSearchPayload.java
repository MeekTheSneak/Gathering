package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: show me this page of this collection.
 *
 * <p>The search crosses rather than the collection. Answered only for a collection block the
 * player is standing next to, because a payload naming a position is a payload naming any
 * position: without that check this would be a way to read every collection on the server
 * from anywhere on it. Reading one is public, standing in front of it is not.
 */
public record CollectionSearchPayload(
        BlockPos where, CollectionQuery query, boolean descending, int page)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionSearchPayload> TYPE =
            GatheringPayloads.type("collection_search");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionSearchPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionSearchPayload::where,
                    CollectionQuery.STREAM_CODEC, CollectionSearchPayload::query,
                    ByteBufCodecs.BOOL, CollectionSearchPayload::descending,
                    ByteBufCodecs.VAR_INT, CollectionSearchPayload::page,
                    CollectionSearchPayload::new);

    public CollectionSearchPayload {
        query = query == null ? CollectionQuery.EVERYTHING : query;
        page = Math.max(0, page);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
