package dev.gathering.network;

import dev.gathering.item.CardComponent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: one thing somebody did at a trade table.
 * <p>Every verb the table has, in one payload, because they are one conversation and the
 * server answers all of them the same way - by sending both people the whole table back.
 * <p>It does not say <em>whose</em> trade: a player is in at most one at a time and the server
 * knows which, so a payload naming a player's trade would be a payload that could name
 * somebody else's. What it does carry, on the one action that needs it, is which table the
 * player was looking at - the trade's own random identity and the revision within it. Both,
 * because every table starts at revision zero: an agreement left over from a closed trade
 * names a revision the next trade between the same two people also has, and an audit used
 * exactly that to strike a second trade on terms its sender never saw.
 */
public record TradeActionPayload(
        Action action, java.util.Optional<CardComponent> card, int howMany,
        java.util.Optional<java.util.UUID> table, int revision)
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
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                    TradeActionPayload::table,
                    ByteBufCodecs.VAR_INT, TradeActionPayload::revision,
                    TradeActionPayload::new);

    public static TradeActionPayload put(CardComponent card, int howMany) {
        return new TradeActionPayload(Action.PUT, java.util.Optional.of(card),
                Math.max(0, howMany), java.util.Optional.empty(), 0);
    }

    /** Everything except putting a card up, none of which names one. */
    public static TradeActionPayload of(Action action) {
        return new TradeActionPayload(
                action, java.util.Optional.empty(), 0, java.util.Optional.empty(), 0);
    }

    /**
     * Agreeing, and to which table.
     * <p>The table and the revision are the ones the player was looking at when they pressed
     * it. Every change to either offer moves the revision on, so an agreement in flight while
     * the terms changed arrives naming terms that are no longer there; every trade has its
     * own random identity, so one in flight while the trade itself closed arrives naming a
     * table that has gone. Either way it is refused rather than struck. An audit reproduced
     * both: one card traded for nothing, agreed to by somebody who never saw the table.
     */
    public static TradeActionPayload agreeTo(java.util.UUID table, int revision) {
        return new TradeActionPayload(Action.AGREE, java.util.Optional.empty(), 0,
                java.util.Optional.ofNullable(table), revision);
    }

    public TradeActionPayload {
        card = card == null ? java.util.Optional.empty() : card;
        table = table == null ? java.util.Optional.empty() : table;
        howMany = Math.max(0, howMany);
        revision = Math.max(0, revision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
