package dev.gathering.network;

import dev.gathering.item.CardComponent;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A deck somebody has finished building at a collection block, on its way to the server.
 *
 * <p>The whole deck at once rather than a card at a time, because that is what the gesture is:
 * a builder is a list somebody assembles and then commits, and committing it one packet per
 * card would be a hundred round trips in which the deck exists in neither place.
 *
 * <p><b>Nothing here is believed.</b> The client says which printings it wants; the server
 * checks every one of them against what the collection actually holds and takes them out
 * itself. A client that asks for a card the box does not have gets a deck without it and a
 * line saying so - the same answer somebody would get for asking out loud.
 *
 * @param commander the card in the command zone, or empty for a deck with no commander. Kept
 *                  apart from the rest rather than flagged inside it, because it goes to a
 *                  different pile of the deck it becomes
 */
public record BuildDeckPayload(
        BlockPos where,
        String name,
        String description,
        List<CardComponent> cards,
        Optional<CardComponent> commander,
        dev.gathering.core.card.Sleeve sleeve)
        implements CustomPacketPayload {

    /** As many as a deck holds. Past this is a clipboard, not a deck. */
    public static final int MOST_CARDS = dev.gathering.item.DeckComponent.MAX_CARDS;

    /** Long enough for any name somebody means, short enough not to be a payload of its own. */
    public static final int LONGEST_NAME = 64;

    public static final int LONGEST_DESCRIPTION = 256;

    public static final CustomPacketPayload.Type<BuildDeckPayload> TYPE =
            GatheringPayloads.type("build_deck");

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildDeckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BuildDeckPayload::where,
                    ByteBufCodecs.stringUtf8(LONGEST_NAME), BuildDeckPayload::name,
                    ByteBufCodecs.stringUtf8(LONGEST_DESCRIPTION), BuildDeckPayload::description,
                    CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CARDS)),
                    BuildDeckPayload::cards,
                    ByteBufCodecs.optional(CardComponent.STREAM_CODEC), BuildDeckPayload::commander,
                    // Six is what composite() takes in this version, and this is the sixth.
                    ByteBufCodecs.idMapper(
                            dev.gathering.core.card.Sleeve::byOrdinal,
                            dev.gathering.core.card.Sleeve::ordinal),
                    BuildDeckPayload::sleeve,
                    BuildDeckPayload::new);

    public BuildDeckPayload {
        // Bounded on the record as well as in the codec. The codec guards the socket; this
        // guards every other way one of these can be made, and a bound stated once is a bound
        // somebody moves without noticing the other.
        name = trimmed(name, LONGEST_NAME);
        description = trimmed(description, LONGEST_DESCRIPTION);
        cards = cards == null ? List.of() : List.copyOf(cards.subList(0, Math.min(cards.size(), MOST_CARDS)));
        commander = commander == null ? Optional.empty() : commander;
        sleeve = sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
    }

    private static String trimmed(String value, int longest) {
        if (value == null) {
            return "";
        }
        return value.length() <= longest ? value : value.substring(0, longest);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
