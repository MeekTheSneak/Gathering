package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: I am in, or I am not.
 *
 * <p>Which seat this is comes from the server's own record of who is sitting where, never
 * from the payload - so there is no way to phrase an answer on somebody else's behalf, which
 * matters more here than anywhere else in the mod.
 */
public record AnteAnswerPayload(BlockPos table, boolean in) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnteAnswerPayload> TYPE =
            GatheringPayloads.type("ante_answer");

    public static final StreamCodec<RegistryFriendlyByteBuf, AnteAnswerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AnteAnswerPayload::table,
                    ByteBufCodecs.BOOL, AnteAnswerPayload::in,
                    AnteAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
