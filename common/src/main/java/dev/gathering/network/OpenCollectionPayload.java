package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: open this collection.
 *
 * <p>Carries what the screen needs before it has asked for anything - whose it is, how big it
 * is, and what this player is allowed to do with it - and no cards at all. The cards arrive a
 * page at a time in answer to a search, because a collection is meant to run to ten thousand
 * of them and a screen shows forty.
 *
 * <p>What a player may do is decided on the server and sent rather than worked out here: a
 * client that decided for itself would be a client that could decide differently.
 */
public record OpenCollectionPayload(
        BlockPos where, String label, int total, int distinct, boolean mayTake, boolean mayAdd)
        implements CustomPacketPayload {

    public static final int MOST_LABEL_CHARACTERS = 64;

    public static final CustomPacketPayload.Type<OpenCollectionPayload> TYPE =
            GatheringPayloads.type("open_collection");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCollectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenCollectionPayload::where,
                    ByteBufCodecs.stringUtf8(MOST_LABEL_CHARACTERS), OpenCollectionPayload::label,
                    ByteBufCodecs.VAR_INT, OpenCollectionPayload::total,
                    ByteBufCodecs.VAR_INT, OpenCollectionPayload::distinct,
                    ByteBufCodecs.BOOL, OpenCollectionPayload::mayTake,
                    ByteBufCodecs.BOOL, OpenCollectionPayload::mayAdd,
                    OpenCollectionPayload::new);

    public OpenCollectionPayload {
        label = label == null ? "" : label;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
