package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: joining the game at this table, or watching it, from the chair at a seat.
 * <p>Answered only for a player still sitting in that chair and watching that table; anything else is a
 * screen left open after they got up, and gets nothing.
 */
public record JoinTableAnswerPayload(BlockPos table, boolean join) implements AtATable {

    public static final CustomPacketPayload.Type<JoinTableAnswerPayload> TYPE =
            GatheringPayloads.type("join_table_answer");

    public static final StreamCodec<RegistryFriendlyByteBuf, JoinTableAnswerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, JoinTableAnswerPayload::table,
                    ByteBufCodecs.BOOL, JoinTableAnswerPayload::join,
                    JoinTableAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
