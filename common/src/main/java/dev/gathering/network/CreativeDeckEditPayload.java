package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, from the creative menu only: these cards went into the deck with this handle.
 * <p>The creative menu sends the server each slot the client changed, and a deck's slot crosses with its
 * cards hidden - so what went in is said here instead. Honored only for a player in creative mode. See
 * {@code DeckVault}.
 */
public record CreativeDeckEditPayload(UUID deck, List<CardComponent> cards) implements CustomPacketPayload {

    /** A right-click puts one stack into a deck, and a card stack is one card. */
    public static final int MOST_CARDS = 64;

    public static final CustomPacketPayload.Type<CreativeDeckEditPayload> TYPE = GatheringPayloads.type("creative_deck_edit");

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeDeckEditPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CreativeDeckEditPayload::deck,
            CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CARDS)), CreativeDeckEditPayload::cards,
            CreativeDeckEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
