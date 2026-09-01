package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: put these cards, which I am carrying, into the deck in this hand.
 *
 * <p>The gesture this exists for is the one that took the longest in the game. Loose cards go
 * into a deck a right-click at a time, one slot at a time, and a player who has just opened
 * six boosters is looking at forty slots and forty right-clicks. This is the same job as one
 * press of Finish on a screen where the cards were picked out.
 *
 * <p><b>Nothing here is believed.</b> The client names printings it thinks it is carrying;
 * the server looks for each one in that player's own inventory and takes it out itself. A
 * client that names a card it does not have gets a deck without it and a line saying so -
 * which is the same answer {@link BuildDeckPayload} gives for a card the box did not hold.
 *
 * <p>Only ever the sender's own inventory and the sender's own hand, so unlike the collection
 * there is nobody else's property to check the rights on.
 */
public record PocketCardsPayload(boolean offHand, List<CardComponent> cards)
        implements CustomPacketPayload {

    /**
     * The most that may be asked for at once.
     *
     * <p>A deck is the natural bound: nothing larger can be put into one, and the number
     * crosses the wire, so a request for two billion cards must be refused rather than
     * allocated a list.
     */
    public static final int MOST_AT_ONCE = dev.gathering.item.DeckComponent.MAX_CARDS;

    public PocketCardsPayload {
        // Bounded on the record as well as in the codec: the codec guards the socket, this
        // guards every other way one of these can be made.
        cards = cards == null
                ? List.of()
                : List.copyOf(cards.subList(0, Math.min(cards.size(), MOST_AT_ONCE)));
    }

    public static final CustomPacketPayload.Type<PocketCardsPayload> TYPE =
            GatheringPayloads.type("pocket_cards");

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketCardsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PocketCardsPayload::offHand,
                    CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_AT_ONCE)),
                    PocketCardsPayload::cards,
                    PocketCardsPayload::new);

    public static PocketCardsPayload of(InteractionHand hand, List<CardComponent> cards) {
        return new PocketCardsPayload(hand == InteractionHand.OFF_HAND, cards);
    }

    public InteractionHand hand() {
        return offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
