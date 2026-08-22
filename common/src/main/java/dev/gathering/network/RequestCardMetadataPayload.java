package dev.gathering.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "I am holding these printings and do not know what they are."
 *
 * <p>A client's card metadata is cleared on disconnect, because what one server told it is
 * not true of the next. So a deck imported last session is a list of Scryfall ids and
 * nothing else until the client asks - which it does when the player opens the deck.
 *
 * <p>This asks about cards the client demonstrably already holds, so it grants no access it
 * did not have. It is not a general "tell me about any card" channel and must not become
 * one: the server sends hidden-zone identity when the visibility rules say so, never on
 * request.
 */
public record RequestCardMetadataPayload(List<UUID> printings) implements CustomPacketPayload {

    /**
     * A deck is the largest legitimate source of one of these, so its own card limit is the
     * right ceiling: a deck can never hold more distinct printings than it holds cards.
     */
    public static final int MAX_REQUESTED = dev.gathering.item.DeckComponent.MAX_CARDS;

    public static final CustomPacketPayload.Type<RequestCardMetadataPayload> TYPE =
            GatheringPayloads.type("request_card_metadata");

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCardMetadataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_REQUESTED)),
                    RequestCardMetadataPayload::printings,
                    RequestCardMetadataPayload::new);

    public RequestCardMetadataPayload {
        printings = printings == null ? List.of() : List.copyOf(printings);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
