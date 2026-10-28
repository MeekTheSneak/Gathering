package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: let this player in, or shut them out.
 * <p>All three rights in one message rather than three messages, because they are set from
 * three boxes on one row and a player half changed is a row that disagrees with itself. Every
 * one false shuts them out entirely.
 * <p>A name rather than an id: the owner is typing it, and a client that sent an id would be a
 * client that had been told who is on this server.
 */
public record CollectionKeyPayload(BlockPos where, String name, boolean look, boolean take, boolean add)
        implements CustomPacketPayload {

    /** Longer than any Minecraft name, and short enough that nobody can send a book through it. */
    public static final int MOST_NAME_CHARACTERS = 64;

    public static final CustomPacketPayload.Type<CollectionKeyPayload> TYPE =
            GatheringPayloads.type("collection_key");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionKeyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionKeyPayload::where,
                    ByteBufCodecs.stringUtf8(MOST_NAME_CHARACTERS), CollectionKeyPayload::name,
                    ByteBufCodecs.BOOL, CollectionKeyPayload::look,
                    ByteBufCodecs.BOOL, CollectionKeyPayload::take,
                    ByteBufCodecs.BOOL, CollectionKeyPayload::add,
                    CollectionKeyPayload::new);

    public CollectionKeyPayload {
        name = name == null ? "" : name;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
