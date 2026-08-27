package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: go and get a basic land out of my deck.
 *
 * <p>Out of the library, which is what fetching means. The server looks: a deck with no
 * Forests in it produces no Forest and says so. This used to make a token off a card lookup
 * instead, which was quick to write and wrong - it turned a search into a conjuring trick and
 * a deck's land count into a suggestion.
 *
 * <p><b>Six answers, not a name.</b> A payload carrying a string would be a door a client
 * could put anything through, and what is on the other side of this one walks a library.
 * There are six basic lands, so there are six values here, and an id this build has no name
 * for is refused rather than guessed at.
 *
 * <p>Carries no seat: it is your own deck, and which deck that is comes from the player the
 * packet arrived from.
 */
public record FetchBasicPayload(BlockPos table, Basic land, int count) implements CustomPacketPayload {

    /**
     * The basic lands, by their printed names.
     *
     * <p>Wastes is here because it is a basic land, prints as one and is fetched by the same
     * effects; leaving it out would be a list that is right about Magic until somebody plays
     * colourless.
     */
    public enum Basic {
        PLAINS("Plains"),
        ISLAND("Island"),
        SWAMP("Swamp"),
        MOUNTAIN("Mountain"),
        FOREST("Forest"),
        WASTES("Wastes");

        private final String printedName;

        Basic(String printedName) {
            this.printedName = printedName;
        }

        /** Exactly as it is printed, which is what the card lookup is given. */
        public String printedName() {
            return printedName;
        }

        static final StreamCodec<io.netty.buffer.ByteBuf, Basic> STREAM_CODEC =
                ByteBufCodecs.idMapper(Basic::byId, Basic::ordinal);

        private static Basic byId(int id) {
            Basic[] lands = values();
            if (id < 0 || id >= lands.length) {
                throw new io.netty.handler.codec.DecoderException("Unknown basic land " + id);
            }
            return lands[id];
        }
    }

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
                    Basic.STREAM_CODEC, FetchBasicPayload::land,
                    ByteBufCodecs.VAR_INT, FetchBasicPayload::count,
                    FetchBasicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
