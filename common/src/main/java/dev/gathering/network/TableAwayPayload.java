package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the seats at this table whose players are away from the board, sent beside the board.
 * <p>Seconds rather than a time of day, because a client's clock is its own: the client counts down from
 * the moment this arrives.
 */
public record TableAwayPayload(BlockPos table, List<Away> seats) implements AtATable {

    /**
     * One seat kept for a player away from the board.
     *
     * @param seat        the seat's index at the table
     * @param secondsLeft how long it is kept for
     * @param votes       how many of the others have voted to free it
     * @param needed      how many votes free it: every other player at the table, or zero where voting is not
     *                    allowed
     * @param mayVote     whether the player this is sent to may vote, and has not yet
     */
    public record Away(int seat, int secondsLeft, int votes, int needed, boolean mayVote) {

        static final StreamCodec<RegistryFriendlyByteBuf, Away> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Away::seat,
                ByteBufCodecs.VAR_INT, Away::secondsLeft,
                ByteBufCodecs.VAR_INT, Away::votes,
                ByteBufCodecs.VAR_INT, Away::needed,
                ByteBufCodecs.BOOL, Away::mayVote,
                Away::new);
    }

    /** More seats than a table has. */
    public static final int MOST = 16;

    public static final CustomPacketPayload.Type<TableAwayPayload> TYPE = GatheringPayloads.type("table_away");

    public static final StreamCodec<RegistryFriendlyByteBuf, TableAwayPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TableAwayPayload::table,
            Away.CODEC.apply(ByteBufCodecs.list(MOST)), TableAwayPayload::seats,
            TableAwayPayload::new);

    public TableAwayPayload {
        seats = List.copyOf(seats);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
