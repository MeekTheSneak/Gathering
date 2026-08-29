package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: which cards of this set is this collection missing?
 *
 * <p>Asked rather than worked out on the client, for the reason every other collection
 * question is: what a set contains is a fetch from Scryfall, and the cards to subtract are on
 * the server.
 *
 * <p>The set code is a string, which is the one thing on this payload worth being careful
 * about - it names something the server will go and look up. It is bounded here to the length
 * a set code can be, and checked on arrival against the list of sets the server already
 * knows, so nothing a client sends can send the server looking for a set that does not exist.
 * See {@link dev.gathering.server.CollectionSets}.
 */
public record AskSetMissingPayload(BlockPos collection, String setCode)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AskSetMissingPayload> TYPE =
            GatheringPayloads.type("ask_set_missing");

    public static final StreamCodec<RegistryFriendlyByteBuf, AskSetMissingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AskSetMissingPayload::collection,
                    ByteBufCodecs.stringUtf8(SetProgressPayload.LONGEST_CODE),
                    AskSetMissingPayload::setCode,
                    AskSetMissingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
