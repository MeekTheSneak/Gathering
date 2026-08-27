package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: take back what I just did.
 *
 * <p>Carries how many actions to rewind and nothing else. Not who is asking - that is the
 * player the packet arrived from, looked up at the table - and not whether it is allowed,
 * which is the session's decision and is made again on arrival whatever a client believes.
 * A client that asked to rewind somebody else's turn would be refused by the same code that
 * refuses it in the interface.
 */
public record UndoPayload(BlockPos table, int actions) implements CustomPacketPayload {

    /** More than this in one request is a client that is not asking in good faith. */
    public static final int MOST_AT_ONCE = 32;

    public UndoPayload {
        // Clamped here like every sibling payload, so the cap holds whoever handles it.
        actions = Math.max(1, Math.min(MOST_AT_ONCE, actions));
    }

    public static final CustomPacketPayload.Type<UndoPayload> TYPE =
            GatheringPayloads.type("undo");

    public static final StreamCodec<RegistryFriendlyByteBuf, UndoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UndoPayload::table,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, UndoPayload::actions,
                    UndoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
