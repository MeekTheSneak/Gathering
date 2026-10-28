package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: one thing done at a signup.
 * <p>The player is the connection, never the payload: "call it off" is only obeyed from the
 * host, and "take mine back" only ever returns the sender's own packs.
 */
public record PodActionPayload(BlockPos table, Action action) implements AtATable {

    public enum Action {
        /** Show me the signup. */
        VIEW,
        /** Put in the packs I am carrying that the event will take. */
        PUT_IN,
        /** Give me my packs back. */
        WITHDRAW,
        /** Open the packs and begin. Host only. */
        START,
        /** Call it off, handing every pack back. Host only. */
        CANCEL
    }

    public static final CustomPacketPayload.Type<PodActionPayload> TYPE = GatheringPayloads.type("pod_action");

    public static final StreamCodec<RegistryFriendlyByteBuf, PodActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PodActionPayload::table,
                    ByteBufCodecs.VAR_INT.map(
                            index -> Action.values()[Math.clamp(index, 0, Action.values().length - 1)],
                            Action::ordinal),
                    PodActionPayload::action,
                    PodActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
