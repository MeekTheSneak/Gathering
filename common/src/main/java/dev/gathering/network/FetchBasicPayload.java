package dev.gathering.network;

import dev.gathering.core.card.BasicLand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: go and get a basic land out of my deck.
 * <p>Out of the library, which is what fetching means. The server looks: a deck with no
 * Forests in it produces no Forest and says so. This used to make a token off a card lookup
 * instead, which was quick to write and wrong - it turned a search into a conjuring trick and
 * a deck's land count into a suggestion.
 * <p><b>Six answers, not a name.</b> A payload carrying a string would be a door a client
 * could put anything through, and what is on the other side of this one walks a library.
 * There are six basic lands, so there are six values here, and an id this build has no name
 * for is refused rather than guessed at.
 * <p>Carries no seat: it is your own deck, and which deck that is comes from the player the
 * packet arrived from.
 */
public record FetchBasicPayload(BlockPos table, BasicLand land, int count)
        implements CustomPacketPayload {


    /** Enough for a land drop and a Cultivate, and short of a board full of Islands. */
    public static final int MOST = 10;

    public FetchBasicPayload {
        count = Math.max(1, Math.min(MOST, count));
    }

    public static final CustomPacketPayload.Type<FetchBasicPayload> TYPE =
            GatheringPayloads.type("fetch_basic");

    public static final StreamCodec<RegistryFriendlyByteBuf, FetchBasicPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FetchBasicPayload::table,
                    AddBasicsPayload.LAND_CODEC, FetchBasicPayload::land,
                    ByteBufCodecs.VAR_INT, FetchBasicPayload::count,
                    FetchBasicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
