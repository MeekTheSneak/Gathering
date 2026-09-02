package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "I did this at that table."
 * <p>Carries an encoded game event. The server decodes it and then refuses it unless the
 * event's actor is the sender's own seat - a client can describe any move it likes, and the
 * only one it may sign is its own. Everything after that is {@code Authorization}, which is
 * the same gate a server-side action goes through.
 * <p>Attribution is the honesty mechanism the whole design rests on: the log says who did
 * what, so "any seated player may move any public card" is safe. A client that could put
 * somebody else's name on a move would take that away.
 */
public record TableActionPayload(BlockPos table, byte[] event) implements CustomPacketPayload {

    /** One event, generously. A move is tens of bytes; a deck load is the big one. */
    public static final int MAX_BYTES = 1 << 16;

    public static final CustomPacketPayload.Type<TableActionPayload> TYPE =
            GatheringPayloads.type("table_action");

    public static final StreamCodec<RegistryFriendlyByteBuf, TableActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TableActionPayload::table,
                    ByteBufCodecs.byteArray(MAX_BYTES), TableActionPayload::event,
                    TableActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
