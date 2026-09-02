package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: show me this page of this collection.
 * <p>The search crosses rather than the collection, and how much of it will fit crosses with
 * it. Answered only for a collection block the
 * player is standing next to, because a payload naming a position is a payload naming any
 * position: without that check this would be a way to read every collection on the server
 * from anywhere on it. Reading one is public, standing in front of it is not.
 *
 * @param pockets whether the loose cards this player is carrying count as part of the pool.
 *                The builder says yes and the collection screen says no, and they mean
 *                different things by the same search: a deck is built out of everything you
 *                own, but a binder shows what is in the binder
 */
public record CollectionSearchPayload(
        BlockPos where, CollectionQuery query, boolean descending, int page, int perPage,
        boolean pockets) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionSearchPayload> TYPE =
            GatheringPayloads.type("collection_search");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionSearchPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionSearchPayload::where,
                    CollectionQuery.STREAM_CODEC, CollectionSearchPayload::query,
                    ByteBufCodecs.BOOL, CollectionSearchPayload::descending,
                    ByteBufCodecs.VAR_INT, CollectionSearchPayload::page,
                    ByteBufCodecs.VAR_INT, CollectionSearchPayload::perPage,
                    ByteBufCodecs.BOOL, CollectionSearchPayload::pockets,
                    CollectionSearchPayload::new);

    public CollectionSearchPayload {
        query = query == null ? CollectionQuery.EVERYTHING : query;
        page = Math.max(0, page);
        // How many rows the window has room for. Asked by the screen because the screen is
        // the thing with a height: a page bigger than the box is rows nobody can see and,
        // worse, rows somebody can click on without seeing.
        perPage = Math.clamp(perPage, 1, CollectionPagePayload.ROWS_PER_PAGE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
