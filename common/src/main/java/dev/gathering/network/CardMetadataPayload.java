package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: display metadata for cards this client is entitled to see.
 *
 * <p>The one channel by which a client learns what a card is. Nothing is sent
 * speculatively: the server sends a summary when, and only when, the visibility rules put
 * that card in this client's view. That is the whole security property, and it is why a
 * spectating client is incapable of leaking a hand even if modified - it was never told.
 */
public record CardMetadataPayload(List<CardSummary> cards) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardMetadataPayload> TYPE =
            GatheringPayloads.type("card_metadata");

    public static final StreamCodec<RegistryFriendlyByteBuf, CardMetadataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    CardSummary.LIST_STREAM_CODEC, CardMetadataPayload::cards,
                    CardMetadataPayload::new);

    public CardMetadataPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /**
     * How many summaries travel in one packet.
     *
     * <p>The game refuses to write a custom payload over a megabyte, and refusing to write
     * one disconnects the player it was for. A summary carries a name, a mana cost, a type
     * line, oracle text and two image links, per face; a wordy two-faced card runs to a
     * couple of kilobytes. Two hundred and fifty-six of those is a comfortable fraction of
     * the limit even at that size, and a typical one is a tenth of it.
     *
     * <p>Not a number of cards anybody has: it is the point at which one send becomes two,
     * and both arrive.
     */
    public static final int MOST_PER_PACKET = 256;

    /**
     * Splits a run of summaries into packets the game will actually write.
     *
     * <p>Every caller that can hold more than a handful goes through here, because the size
     * a send may reach is a property of the packet rather than of any one thing sending it -
     * and a limit each sender remembers separately is a limit one of them forgets. A deck
     * import is a whole cube; a table's public cards are several decks at once.
     */
    public static List<CardMetadataPayload> inPackets(List<CardSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        if (summaries.size() <= MOST_PER_PACKET) {
            return List.of(new CardMetadataPayload(summaries));
        }
        List<CardMetadataPayload> packets = new java.util.ArrayList<>(
                (summaries.size() + MOST_PER_PACKET - 1) / MOST_PER_PACKET);
        for (int from = 0; from < summaries.size(); from += MOST_PER_PACKET) {
            packets.add(new CardMetadataPayload(summaries.subList(
                    from, Math.min(from + MOST_PER_PACKET, summaries.size()))));
        }
        return List.copyOf(packets);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
