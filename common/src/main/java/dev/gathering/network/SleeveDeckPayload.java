package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: "sleeve this deck in that".
 * <p>Its own payload rather than a deck edit, for the reason a rename is: a deck edit is
 * cards crossing between piles, and this is neither a card nor a pile.
 * <p>A place in the list rather than a name, and read back through {@link
 * dev.gathering.core.card.Sleeve#byOrdinal}, so a number off a socket picks a sleeve that
 * exists or picks the ordinary one. Nothing a client sends decides anything but what its own
 * deck looks like from behind.
 *
 * @param offHand which hand holds the deck; {@link InteractionHand} has no vanilla stream
 *                codec, and a boolean is the whole of it
 */
public record SleeveDeckPayload(boolean offHand, int sleeve) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleeveDeckPayload> TYPE =
            GatheringPayloads.type("sleeve_deck");

    public static final StreamCodec<RegistryFriendlyByteBuf, SleeveDeckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SleeveDeckPayload::offHand,
                    ByteBufCodecs.VAR_INT, SleeveDeckPayload::sleeve,
                    SleeveDeckPayload::new);

    public static SleeveDeckPayload of(InteractionHand hand, dev.gathering.core.card.Sleeve sleeve) {
        return new SleeveDeckPayload(hand == InteractionHand.OFF_HAND, sleeve.ordinal());
    }

    public InteractionHand hand() {
        return offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** Whatever was asked for, or the ordinary back. Never throws; this comes off a socket. */
    public dev.gathering.core.card.Sleeve chosen() {
        return dev.gathering.core.card.Sleeve.byOrdinal(sleeve);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
