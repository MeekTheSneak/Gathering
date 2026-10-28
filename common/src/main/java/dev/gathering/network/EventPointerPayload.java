package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: where this player's seat is, to point the way; or nothing, to stop.
 *
 * @param seat  where to stand
 * @param table the table's number, or 0 to stop pointing
 */
public record EventPointerPayload(BlockPos seat, int table) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EventPointerPayload> TYPE = GatheringPayloads.type("event_pointer");

    public static final StreamCodec<RegistryFriendlyByteBuf, EventPointerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EventPointerPayload::seat,
            ByteBufCodecs.VAR_INT, EventPointerPayload::table,
            EventPointerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
