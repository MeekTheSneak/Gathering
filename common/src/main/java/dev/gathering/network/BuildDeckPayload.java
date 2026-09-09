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
 * <p>The whole deck at once rather than a card at a time, because that is what the gesture is:
 * a builder is a list somebody assembles and then commits, and committing it one packet per
 * card would be a hundred round trips in which the deck exists in neither place.
 * <p><b>Nothing here is believed.</b> The client says which printings it wants; the server
 * checks every one of them against what the collection actually holds and takes them out
 * itself. A client that asks for a card the box does not have gets a deck without it and a
 * line saying so - the same answer somebody would get for asking out loud.
 *
 * @param commander the card in the command zone, or empty for a deck with no commander. Kept
 *                  apart from the rest rather than flagged inside it, because it goes to a
 *                  different pile of the deck it becomes
 * @param request   which press this is, echoed back in the result. The builder waits for the
 *                  server before it closes, so it has to be able to tell its own answer from
 *                  one meant for a screen that has since been closed and reopened
 */
public record BuildDeckPayload(
        BlockPos where,
        String name,
        String description,
        List<CardComponent> cards,
        Optional<CardComponent> commander,
        dev.gathering.core.card.Sleeve sleeve,
        Optional<java.util.UUID> request)
        implements CustomPacketPayload {

    /** A press nobody needs to hear the answer to by name - a command, or a test. */
    public BuildDeckPayload(
            BlockPos where, String name, String description, List<CardComponent> cards,
            Optional<CardComponent> commander, dev.gathering.core.card.Sleeve sleeve) {
        this(where, name, description, cards, commander, sleeve, Optional.empty());
    }

    /** As many as a deck holds. Past this is a clipboard, not a deck. */
    public static final int MOST_CARDS = dev.gathering.item.DeckComponent.MAX_CARDS;

    /** Long enough for any name somebody means, short enough not to be a payload of its own. */
    public static final int LONGEST_NAME = 64;

    public static final int LONGEST_DESCRIPTION = 256;

    public static final CustomPacketPayload.Type<BuildDeckPayload> TYPE =
            GatheringPayloads.type("build_deck");

    /**
     * Written out by hand rather than composed.
     * <p>{@code StreamCodec.composite} takes six parts in this version and this has seven,
     * the seventh being which press the result belongs to. The only thing to keep right is
     * that the two halves stay in step.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildDeckPayload> STREAM_CODEC =
            StreamCodec.of(BuildDeckPayload::toNetwork, BuildDeckPayload::fromNetwork);

    private static final StreamCodec<ByteBuf, dev.gathering.core.card.Sleeve> SLEEVE =
            ByteBufCodecs.idMapper(
                    dev.gathering.core.card.Sleeve::byOrdinal,
                    dev.gathering.core.card.Sleeve::ordinal);

    private static void toNetwork(RegistryFriendlyByteBuf out, BuildDeckPayload asked) {
        BlockPos.STREAM_CODEC.encode(out, asked.where());
        ByteBufCodecs.stringUtf8(LONGEST_NAME).encode(out, asked.name());
        ByteBufCodecs.stringUtf8(LONGEST_DESCRIPTION).encode(out, asked.description());
        CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CARDS)).encode(out, asked.cards());
        ByteBufCodecs.optional(CardComponent.STREAM_CODEC).encode(out, asked.commander());
        SLEEVE.encode(out, asked.sleeve());
        ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC).encode(out, asked.request());
    }

    private static BuildDeckPayload fromNetwork(RegistryFriendlyByteBuf in) {
        BlockPos where = BlockPos.STREAM_CODEC.decode(in);
        String name = ByteBufCodecs.stringUtf8(LONGEST_NAME).decode(in);
        String description = ByteBufCodecs.stringUtf8(LONGEST_DESCRIPTION).decode(in);
        List<CardComponent> cards =
                CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CARDS)).decode(in);
        Optional<CardComponent> commander =
                ByteBufCodecs.optional(CardComponent.STREAM_CODEC).decode(in);
        dev.gathering.core.card.Sleeve sleeve = SLEEVE.decode(in);
        return new BuildDeckPayload(where, name, description, cards, commander, sleeve,
                ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC).decode(in));
    }

    public BuildDeckPayload {
        // Bounded on the record as well as in the codec. The codec guards the socket; this
        // guards every other way one of these can be made, and a bound stated once is a bound
        // somebody moves without noticing the other.
        name = trimmed(name, LONGEST_NAME);
        description = trimmed(description, LONGEST_DESCRIPTION);
        cards = cards == null ? List.of() : List.copyOf(cards.subList(0, Math.min(cards.size(), MOST_CARDS)));
        commander = commander == null ? Optional.empty() : commander;
        sleeve = sleeve == null ? dev.gathering.core.card.Sleeve.DEFAULT : sleeve;
        request = request == null ? Optional.empty() : request;
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
