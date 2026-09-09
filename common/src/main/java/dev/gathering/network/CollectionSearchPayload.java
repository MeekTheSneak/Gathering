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
        boolean pockets, int revision) implements CustomPacketPayload {

    /** For a caller with no screen keeping count, which is every one but the two grids. */
    public CollectionSearchPayload(
            BlockPos where, CollectionQuery query, boolean descending, int page, int perPage,
            boolean pockets) {
        this(where, query, descending, page, perPage, pockets, 0);
    }

    public static final CustomPacketPayload.Type<CollectionSearchPayload> TYPE =
            GatheringPayloads.type("collection_search");

    /**
     * Written out by hand: {@code composite} stops at six parts and this has seven.
     * <p>The seventh is which request this is, so a page that comes back can be matched to
     * the search it answers. Typing sends one of these per keystroke, the server answers what
     * it can, and the answers do not necessarily arrive in order.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionSearchPayload> STREAM_CODEC =
            StreamCodec.of(CollectionSearchPayload::toNetwork, CollectionSearchPayload::fromNetwork);

    private static void toNetwork(RegistryFriendlyByteBuf out, CollectionSearchPayload asked) {
        BlockPos.STREAM_CODEC.encode(out, asked.where());
        CollectionQuery.STREAM_CODEC.encode(out, asked.query());
        out.writeBoolean(asked.descending());
        ByteBufCodecs.VAR_INT.encode(out, asked.page());
        ByteBufCodecs.VAR_INT.encode(out, asked.perPage());
        out.writeBoolean(asked.pockets());
        ByteBufCodecs.VAR_INT.encode(out, asked.revision());
    }

    private static CollectionSearchPayload fromNetwork(RegistryFriendlyByteBuf in) {
        BlockPos where = BlockPos.STREAM_CODEC.decode(in);
        CollectionQuery query = CollectionQuery.STREAM_CODEC.decode(in);
        boolean descending = in.readBoolean();
        int page = ByteBufCodecs.VAR_INT.decode(in);
        int perPage = ByteBufCodecs.VAR_INT.decode(in);
        boolean pockets = in.readBoolean();
        return new CollectionSearchPayload(
                where, query, descending, page, perPage, pockets, ByteBufCodecs.VAR_INT.decode(in));
    }

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
