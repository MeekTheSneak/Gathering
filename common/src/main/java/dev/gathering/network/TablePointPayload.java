package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: where I am pointing on the table I am sitting at, or that I have stopped.
 * <p>A hint about a body, not a move. Nothing it says changes a card, a seat or a log line, and
 * the server's only interest in it is whether the player sending it is actually sitting at the
 * table they name - see {@code TablePointing}. A client that lies gets its own arm pointed
 * somewhere silly and nothing else.
 * <p>Sent on the client tick rather than on mouse movement, and only when the point has moved
 * far enough to see. A packet per mouse event is a hundred a second per player for an arm.
 *
 * @param table    the cluster's origin block, the same one the board is keyed by
 * @param surfaceX where on the felt, in the surface's own units - see {@code TableTop}
 * @param surfaceY and how far down it
 * @param pointing false when the player has closed the table, stood up, or moved off the felt,
 *     in which case the coordinates mean nothing and the arm goes back to rest
 */
public record TablePointPayload(BlockPos table, float surfaceX, float surfaceY, boolean pointing)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TablePointPayload> TYPE =
            GatheringPayloads.type("table_point");

    public static final StreamCodec<RegistryFriendlyByteBuf, TablePointPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TablePointPayload::table,
                    ByteBufCodecs.FLOAT, TablePointPayload::surfaceX,
                    ByteBufCodecs.FLOAT, TablePointPayload::surfaceY,
                    ByteBufCodecs.BOOL, TablePointPayload::pointing,
                    TablePointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
