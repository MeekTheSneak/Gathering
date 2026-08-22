package dev.gathering.network;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client to server: "do this to the deck I am holding".
 *
 * <p>The deck screen is opened from the stack's own data and never has a container of its
 * own, so there is no menu to route clicks through. This is the substitute, and it is
 * deliberately narrow: it names the hand, not a slot or an item, so the only deck a player
 * can ever edit through it is the one they are holding. The server re-reads that stack and
 * ignores anything that is not a deck.
 *
 * <p>The card is sent by value rather than by row index. Indices are a decklist-order
 * position and would let a stale click land on whichever card happened to move into that
 * row; sending the card means a stale click either removes the card the player saw or does
 * nothing at all.
 *
 * @param offHand which hand holds the deck; {@link InteractionHand} has no vanilla stream
 *                codec, and a boolean is the whole of it
 */
public record DeckEditPayload(
        boolean offHand, Action action, DeckComponent.Section section, CardComponent card)
        implements CustomPacketPayload {

    /** What the player asked for. */
    public enum Action {
        /** Left-click a row: take one copy out of the deck and into the player's hands. */
        TAKE,
        /** Right-click a row: move one copy between the command zone and the mainboard. */
        TOGGLE_COMMANDER;

        static final StreamCodec<io.netty.buffer.ByteBuf, Action> STREAM_CODEC =
                ByteBufCodecs.idMapper(Action::byId, Action::ordinal);

        private static Action byId(int id) {
            Action[] actions = values();
            if (id < 0 || id >= actions.length) {
                throw new io.netty.handler.codec.DecoderException("Unknown deck edit action id " + id);
            }
            return actions[id];
        }
    }

    public static final CustomPacketPayload.Type<DeckEditPayload> TYPE = GatheringPayloads.type("deck_edit");

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DeckEditPayload::offHand,
            Action.STREAM_CODEC, DeckEditPayload::action,
            DeckComponent.Section.STREAM_CODEC, DeckEditPayload::section,
            CardComponent.STREAM_CODEC, DeckEditPayload::card,
            DeckEditPayload::new);

    public static DeckEditPayload take(
            InteractionHand hand, DeckComponent.Section section, CardComponent card) {
        return new DeckEditPayload(hand == InteractionHand.OFF_HAND, Action.TAKE, section, card);
    }

    public static DeckEditPayload toggleCommander(
            InteractionHand hand, DeckComponent.Section section, CardComponent card) {
        return new DeckEditPayload(hand == InteractionHand.OFF_HAND, Action.TOGGLE_COMMANDER, section, card);
    }

    public InteractionHand hand() {
        return offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
