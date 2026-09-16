package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: what anybody at all may do with this collection.
 * <p>Its own message rather than a name of "everybody" on {@link CollectionKeyPayload}: a
 * player called Everybody would otherwise unlock cabinets by existing.
 * <p>The same three rights a named player gets, so "anyone may look and only I may take" is a thing the
 * owner can say rather than a default they have to hope for.
 */
public record CollectionLockPayload(BlockPos where, boolean look, boolean take, boolean add)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionLockPayload> TYPE =
            GatheringPayloads.type("collection_lock");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionLockPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionLockPayload::where,
                    ByteBufCodecs.BOOL, CollectionLockPayload::look,
                    ByteBufCodecs.BOOL, CollectionLockPayload::take,
                    ByteBufCodecs.BOOL, CollectionLockPayload::add,
                    CollectionLockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
