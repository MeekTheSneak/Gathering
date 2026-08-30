package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: show me this page of this collection.
 *
 * <p>The search crosses rather than the collection, and how much of it will fit crosses with
 * it. Answered only for a collection block the
 * player is standing next to, because a payload naming a position is a payload naming any
 * position: without that check this would be a way to read every collection on the server
 * from anywhere on it. Reading one is public, standing in front of it is not.
 */
public record CollectionSearchPayload(
        BlockPos where, CollectionQuery query, boolean descending, int page, int perPage,
        java.util.Optional<java.util.UUID> suggestFor)
        implements CustomPacketPayload {

    /**
     * A shorter way to ask the ordinary question, which is most of them.
     *
     * <p>Suggesting is one screen's need; every other caller wants a plain search and should
     * not have to say "and no commander" to get one.
     */
    public static CollectionSearchPayload of(
            BlockPos where, CollectionQuery query, boolean descending, int page, int perPage) {
        return new CollectionSearchPayload(
                where, query, descending, page, perPage, java.util.Optional.empty());
    }

    public static final CustomPacketPayload.Type<CollectionSearchPayload> TYPE =
            GatheringPayloads.type("collection_search");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionSearchPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionSearchPayload::where,
                    CollectionQuery.STREAM_CODEC, CollectionSearchPayload::query,
                    ByteBufCodecs.BOOL, CollectionSearchPayload::descending,
                    ByteBufCodecs.VAR_INT, CollectionSearchPayload::page,
                    ByteBufCodecs.VAR_INT, CollectionSearchPayload::perPage,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                    CollectionSearchPayload::suggestFor,
                    CollectionSearchPayload::new);

    public CollectionSearchPayload {
        query = query == null ? CollectionQuery.EVERYTHING : query;
        page = Math.max(0, page);
        // How many rows the window has room for. Asked by the screen because the screen is
        // the thing with a height: a page bigger than the box is rows nobody can see and,
        // worse, rows somebody can click on without seeing.
        perPage = Math.clamp(perPage, 1, CollectionPagePayload.ROWS_PER_PAGE);
        // Which commander to rank the box against, when the asking screen is the deck builder
        // on its suggestions tab. Empty is the ordinary search, which is what everything else
        // wants - see CardFit for why this is a reading of your own cards rather than a
        // question asked of somebody else's website.
        suggestFor = suggestFor == null ? java.util.Optional.empty() : suggestFor;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
