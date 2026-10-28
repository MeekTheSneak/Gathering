package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: hand this collection to somebody else.
 * <p>The one thing a collection could not do. Whoever put it down owned it for ever, and because the
 * owner travels in the item, a cabinet whose owner stops playing was locked to everybody with no way
 * back - which the lock on looking made worse rather than better.
 * <p>A name rather than an id, like letting somebody in: the owner is typing it.
 */
public record CollectionOwnerPayload(BlockPos where, String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectionOwnerPayload> TYPE =
            GatheringPayloads.type("collection_owner");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionOwnerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionOwnerPayload::where,
                    ByteBufCodecs.stringUtf8(CollectionKeyPayload.MOST_NAME_CHARACTERS),
                    CollectionOwnerPayload::name,
                    CollectionOwnerPayload::new);

    public CollectionOwnerPayload {
        name = name == null ? "" : name;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
