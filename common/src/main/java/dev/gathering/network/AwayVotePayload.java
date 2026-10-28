package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: I vote to free this seat, whose player is away from the board.
 * <p>Counted only from a player seated at the table and not away themselves, in a game of four or more.
 */
public record AwayVotePayload(BlockPos table, int seat) implements AtATable {

    public static final CustomPacketPayload.Type<AwayVotePayload> TYPE = GatheringPayloads.type("away_vote");

    public static final StreamCodec<RegistryFriendlyByteBuf, AwayVotePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AwayVotePayload::table,
            ByteBufCodecs.VAR_INT, AwayVotePayload::seat,
            AwayVotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
