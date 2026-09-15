package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a pack has been opened, and this is what was in it.
 * <p>Sent once the cards are drawn and written down as this player's, and before they are in the
 * inventory: they arrive when the wrapper is torn, which the client says with the token here. Nothing
 * about tearing decides what is in the pack, and nothing about not tearing loses it - a closed screen
 * tears it, a disconnect hands the cards over on the next join, and a client that says nothing has
 * them handed over after a minute and a half. See {@code PackWrappers}.
 * <p>Which means there is nothing here a modified client could gain by reading early. The cards are
 * already decided and already the player's; this says which of them to make a fuss about.
 *
 * @param setCode which set's wrapper is being torn
 * @param kind    which product, so the wrapper is the right color
 * @param cards   what came out, in the order it should be shown
 * @param wrapper the token the cards wait under until the pack is torn, or blank when they were
 *                handed over already - see {@code PackWrappers}
 */
public record PackOpenedPayload(String setCode, String kind, List<CardComponent> cards, String wrapper)
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
                    ByteBufCodecs.stringUtf8(40), PackOpenedPayload::wrapper,
                    PackOpenedPayload::new);

    public PackOpenedPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
        wrapper = wrapper == null ? "" : wrapper;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
