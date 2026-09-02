package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the board, as this client is entitled to know it.
 * <p>The bytes are a {@code GameView} and never a {@code GameState}. The view has already
 * been through the visibility rules, so an opponent's hand is a number and a face-down card
 * is a marker with no path back to a card - there is nothing in this packet for a modified
 * client to extract, which is the whole design.
 * <p>One of these is addressed to each seated player separately, because each of them is
 * entitled to something different. A single broadcast board is the mistake this shape exists
 * to make impossible.
 */
public record TableViewPayload(BlockPos table, byte[] view, boolean open)
        implements CustomPacketPayload {

    /** A four-player Commander board is far below this; a bound so a bad packet is refused. */
    public static final int MAX_BYTES = 1 << 20;

    public static final CustomPacketPayload.Type<TableViewPayload> TYPE = GatheringPayloads.type("table_view");

    public static final StreamCodec<RegistryFriendlyByteBuf, TableViewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TableViewPayload::table,
                    ByteBufCodecs.byteArray(MAX_BYTES), TableViewPayload::view,
                    // Whether this is an update to a board already on screen or the answer to
                    // somebody sitting down. Without the distinction a closed screen reopens
                    // itself the next time anybody at the table does anything.
                    ByteBufCodecs.BOOL, TableViewPayload::open,
                    TableViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
