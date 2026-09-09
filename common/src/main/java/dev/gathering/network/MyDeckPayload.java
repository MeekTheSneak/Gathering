package dev.gathering.network;

import dev.gathering.item.DeckComponent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Server to client: what is really in the deck you are holding.
 * <p>The deck item itself carries only what a deck is from across the table - its name, its
 * sleeves, its commanders and how thick it is - because an item component goes to every client
 * that can see the item, and a decklist is not everybody's business. See
 * {@link DeckComponent#PUBLIC_STREAM_CODEC}.
 * <p>This is the other half: the real list, sent to one player about a deck in their own hand.
 * Sent when it changes rather than asked for, so a screen that opens onto a deck has it
 * already, and so an edit made on the server is on the screen the moment it lands.
 * <p>The revision says which push this is. The client used to keep one entry per hand and
 * decide whether it was the right deck by comparing the name and the card count against the
 * public copy on the item - which two different decks can match exactly, so a player carrying
 * two sixty-card decks called "Deck" could be shown one list while holding the other for the
 * frame or two before the next push landed. Identity is not a thing to infer from appearance.
 */
public record MyDeckPayload(boolean offHand, int revision, DeckComponent deck)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MyDeckPayload> TYPE =
            GatheringPayloads.type("my_deck");

    public static final StreamCodec<RegistryFriendlyByteBuf, MyDeckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MyDeckPayload::offHand,
                    ByteBufCodecs.VAR_INT, MyDeckPayload::revision,
                    DeckComponent.STREAM_CODEC, MyDeckPayload::deck,
                    MyDeckPayload::new);

    public static MyDeckPayload of(InteractionHand hand, int revision, DeckComponent deck) {
        return new MyDeckPayload(hand == InteractionHand.OFF_HAND, revision, deck);
    }

    public InteractionHand hand() {
        return offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
