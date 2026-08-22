package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: you asked to start a game, so pick what kind.
 *
 * <p>Carries no format list. Both sides ship the same presets, and sending them would make the
 * server's copy authoritative over a client that already has it - the client asks for a format
 * by id and the server is the one that looks it up, which is the check that matters.
 */
public record OpenTableSetupPayload(BlockPos table) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenTableSetupPayload> TYPE =
            GatheringPayloads.type("open_table_setup");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTableSetupPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenTableSetupPayload::table,
                    OpenTableSetupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
