package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: start a game here, of this kind, over this many.
 *
 * <p>The format arrives as an id and the server looks it up, so a client cannot invent a
 * format with a two-card minimum. The match length is checked against the supported ones for
 * the same reason.
 */
public record StartTablePayload(BlockPos table, String formatId, int bestOf)
        implements CustomPacketPayload {

    /** Long enough for any preset id, short enough that a bad one is not a payload. */
    public static final int MAX_FORMAT_ID = 64;

    public static final CustomPacketPayload.Type<StartTablePayload> TYPE =
            GatheringPayloads.type("start_table");

    public static final StreamCodec<RegistryFriendlyByteBuf, StartTablePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StartTablePayload::table,
                    ByteBufCodecs.stringUtf8(MAX_FORMAT_ID), StartTablePayload::formatId,
                    ByteBufCodecs.VAR_INT, StartTablePayload::bestOf,
                    StartTablePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
