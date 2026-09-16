package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: open this collection to everybody, or shut it.
 * <p>Its own message rather than a name of "everybody" on {@link CollectionKeyPayload}: a
 * player called Everybody would otherwise unlock cabinets by existing.
 */
public record CollectionLockPayload(BlockPos where, boolean open) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionLockPayload> TYPE =
            GatheringPayloads.type("collection_lock");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionLockPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionLockPayload::where,
                    ByteBufCodecs.BOOL, CollectionLockPayload::open,
                    CollectionLockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
