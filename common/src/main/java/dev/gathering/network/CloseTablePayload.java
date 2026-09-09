package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the game you were watching is over, so stop watching it.
 * <p>Without this a player whose table's session ended sits looking at the last board it
 * ever sent, which is worse than an empty screen because it looks live.
 * <p>It names the table. It used to name none, and the client cleared every board it knew -
 * so ending one game at a pod of two blanked the other, and a spectator standing between two
 * tables lost both. It also went only to the seated players, leaving anybody watching with a
 * board that still looked live.
 */
public record CloseTablePayload(BlockPos table) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseTablePayload> TYPE =
            GatheringPayloads.type("close_table");

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseTablePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CloseTablePayload::table,
                    CloseTablePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
