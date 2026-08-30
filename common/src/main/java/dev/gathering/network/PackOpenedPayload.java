package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a pack has been opened, and this is what was in it.
 *
 * <p>Sent after the cards are already in the inventory, not before. The ceremony is theater
 * over a transaction that has already happened: nothing about tearing the wrapper decides
 * anything, so a player who closes the screen, disconnects, or never finishes the tear still
 * has every card. A ceremony that had to complete for the cards to arrive would be a way to
 * lose a booster to a dropped connection.
 *
 * <p>Which means there is nothing here a modified client could gain by reading early. It
 * already holds these cards; this says which of them to make a fuss about.
 *
 * @param setCode which set's wrapper is being torn
 * @param kind    which product, so the wrapper is the right color
 * @param cards   what came out, in the order it should be shown
 */
public record PackOpenedPayload(String setCode, String kind, List<CardComponent> cards)
        implements CustomPacketPayload {

    /** A jumpstart pack is twenty and a display box is not one pack. Far above any real one. */
    public static final int MOST_CARDS = 64;

    public static final CustomPacketPayload.Type<PackOpenedPayload> TYPE =
            GatheringPayloads.type("pack_opened");

    public static final StreamCodec<RegistryFriendlyByteBuf, PackOpenedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PackOpenedPayload::setCode,
                    ByteBufCodecs.STRING_UTF8, PackOpenedPayload::kind,
                    CardComponent.STREAM_CODEC.apply(
                            ByteBufCodecs.list(MOST_CARDS)), PackOpenedPayload::cards,
                    PackOpenedPayload::new);

    public PackOpenedPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
