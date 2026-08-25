package dev.gathering.network;

import dev.gathering.item.CardComponent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: one thing somebody did at a trade table.
 *
 * <p>Every verb the table has, in one payload, because they are one conversation and the
 * server answers all of them the same way - by sending both people the whole table back.
 *
 * <p>Nothing here says which trade. A player is in at most one at a time and the server knows
 * which, so a payload that named a trade would be a payload that could name somebody else's.
 */
public record TradeActionPayload(
        Action action, java.util.Optional<CardComponent> card, int howMany)
        implements CustomPacketPayload {

    /** What the player asked for. */
    public enum Action {
        /** Put this many of a card on the table - which is a count, not an increment. */
        PUT,
        /** Take everything back down. */
        CLEAR,
        /** Agree to the table as it stands. */
        AGREE,
        /** Take that agreement back. */
        THINK_AGAIN,
        /** Walk away. */
        CLOSE;

        static final StreamCodec<io.netty.buffer.ByteBuf, Action> STREAM_CODEC =
                ByteBufCodecs.idMapper(Action::byId, Action::ordinal);

        private static Action byId(int id) {
            Action[] actions = values();
            if (id < 0 || id >= actions.length) {
                throw new io.netty.handler.codec.DecoderException(
                        "Unknown trade action id " + id);
            }
            return actions[id];
        }
    }

    public static final CustomPacketPayload.Type<TradeActionPayload> TYPE =
            GatheringPayloads.type("trade_action");

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Action.STREAM_CODEC, TradeActionPayload::action,
                    ByteBufCodecs.optional(CardComponent.STREAM_CODEC),
                    TradeActionPayload::card,
                    ByteBufCodecs.VAR_INT, TradeActionPayload::howMany,
                    TradeActionPayload::new);

    public static TradeActionPayload put(CardComponent card, int howMany) {
        return new TradeActionPayload(
                Action.PUT, java.util.Optional.of(card), Math.max(0, howMany));
    }

    /** Everything except putting a card up, none of which names one. */
    public static TradeActionPayload of(Action action) {
        return new TradeActionPayload(action, java.util.Optional.empty(), 0);
    }

    public TradeActionPayload {
        card = card == null ? java.util.Optional.empty() : card;
        howMany = Math.max(0, howMany);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
