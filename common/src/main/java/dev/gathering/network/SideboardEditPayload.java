package dev.gathering.network;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: move this card between my deck and my sideboard.
 * <p>One card at a time, named by what it is rather than by an index, for the same reason
 * every other deck edit works that way: an index is a promise about a list the server may have
 * changed since, and a card is a card.
 * <p>The server edits the deck it is holding for this player's seat, and no other. Nothing in
 * this payload names a seat, which is the point - a client cannot ask to shuffle somebody
 * else's sideboard because there is nowhere to say whose.
 */
public record SideboardEditPayload(
        BlockPos table, DeckComponent.Section from, DeckComponent.Section to, CardComponent card)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SideboardEditPayload> TYPE =
            GatheringPayloads.type("sideboard_edit");

    public static final StreamCodec<RegistryFriendlyByteBuf, SideboardEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SideboardEditPayload::table,
                    DeckComponent.Section.STREAM_CODEC, SideboardEditPayload::from,
                    DeckComponent.Section.STREAM_CODEC, SideboardEditPayload::to,
                    CardComponent.STREAM_CODEC, SideboardEditPayload::card,
                    SideboardEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
