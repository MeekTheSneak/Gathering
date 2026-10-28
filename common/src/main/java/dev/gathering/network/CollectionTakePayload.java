package dev.gathering.network;

import dev.gathering.item.CardComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: take this many of this card out.
 * <p>Asks; it does not tell. What actually comes out is decided on the server, against what
 * is really in the collection and whether this player may take from it - a client naming a
 * card the collection does not hold gets nothing, and one naming a collection it is not
 * standing at gets nothing either.
 */
public record CollectionTakePayload(BlockPos where, CardComponent card, int howMany)
        implements CustomPacketPayload {

    /** A playset and a bit. More than this is a click nobody meant to make. */
    public static final int MOST_AT_ONCE = 64;

    public static final CustomPacketPayload.Type<CollectionTakePayload> TYPE =
            GatheringPayloads.type("collection_take");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionTakePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionTakePayload::where,
                    CardComponent.STREAM_CODEC, CollectionTakePayload::card,
                    ByteBufCodecs.VAR_INT, CollectionTakePayload::howMany,
                    CollectionTakePayload::new);

    public CollectionTakePayload {
        howMany = Math.clamp(howMany, 1, MOST_AT_ONCE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
