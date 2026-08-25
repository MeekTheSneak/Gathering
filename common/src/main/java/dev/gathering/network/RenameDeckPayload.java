package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: "call this deck something else".
 *
 * <p>Its own payload rather than another action on {@link DeckEditPayload}, because that one
 * is about cards crossing between piles and this one is not - and because a rename is a
 * string, which is the one thing a card edit never carries.
 *
 * <p>Which deck is the one in the hand, named the way every other deck edit names it. Nothing
 * else would do: a deck in a chest across the world is a deck this player is not holding, and
 * a payload that could name one would be a payload that could rename anybody's.
 *
 * @param offHand which hand holds the deck; {@link InteractionHand} has no vanilla stream
 *                codec, and a boolean is the whole of it
 */
public record RenameDeckPayload(boolean offHand, String name) implements CustomPacketPayload {

    /** The same cap the import screen uses, so a deck cannot be named two ways. */
    public static final int MOST_CHARACTERS = ImportDecklistPayload.MAX_NAME_LENGTH;

    public static final CustomPacketPayload.Type<RenameDeckPayload> TYPE =
            GatheringPayloads.type("rename_deck");

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameDeckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, RenameDeckPayload::offHand,
                    ByteBufCodecs.stringUtf8(MOST_CHARACTERS), RenameDeckPayload::name,
                    RenameDeckPayload::new);

    public RenameDeckPayload {
        name = name == null ? "" : name.strip();
        if (name.length() > MOST_CHARACTERS) {
            name = name.substring(0, MOST_CHARACTERS);
        }
    }

    public static RenameDeckPayload of(InteractionHand hand, String name) {
        return new RenameDeckPayload(hand == InteractionHand.OFF_HAND, name);
    }

    public InteractionHand hand() {
        return offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
