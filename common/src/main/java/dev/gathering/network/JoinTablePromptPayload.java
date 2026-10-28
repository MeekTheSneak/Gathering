package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: you sat down at a seat of a game already on, so join it or watch it.
 * <p>Asked before the seat is theirs, from the chair, which holds the seat for the moment it takes to
 * answer: nobody else can sit in a chair somebody is sitting in.
 */
public record JoinTablePromptPayload(BlockPos table) implements AtATable {

    public static final CustomPacketPayload.Type<JoinTablePromptPayload> TYPE =
            GatheringPayloads.type("join_table_prompt");

    public static final StreamCodec<RegistryFriendlyByteBuf, JoinTablePromptPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, JoinTablePromptPayload::table,
                    JoinTablePromptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
