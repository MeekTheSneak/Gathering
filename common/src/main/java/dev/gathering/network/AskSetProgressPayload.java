package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: how much of each set is in that collection?
 * <p>Nothing to carry but the block. Which cards are in it, what set each of them belongs to
 * and how big that set is are all the server's - a client that worked this out itself would
 * need every card in the collection sent to it, which is the megabyte the whole collection
 * screen is arranged to avoid.
 */
public record AskSetProgressPayload(BlockPos collection) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AskSetProgressPayload> TYPE =
            GatheringPayloads.type("ask_set_progress");

    public static final StreamCodec<RegistryFriendlyByteBuf, AskSetProgressPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AskSetProgressPayload::collection,
                    AskSetProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
