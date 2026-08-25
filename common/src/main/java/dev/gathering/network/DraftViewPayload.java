package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the pod, as this drafter is entitled to know it.
 *
 * <p>The bytes are a {@code DraftView} and never a {@code DraftPod}. The view has already
 * been through the pod's visibility rules, so somebody else's pack is a thickness and their
 * pool is a count - there is nothing here for a modified client to extract, which is what
 * makes a draft a draft.
 *
 * <p>One of these is addressed to each drafter separately, for the same reason the board is:
 * each of them is entitled to something different, and a single broadcast pod would hand
 * every client every pack in the ring.
 *
 * @param open whether this opens the pack screen or updates one already showing, so a
 *             drafter who closed the screen to look at something is not dragged back to it
 *             every time a neighbour picks
 */
public record DraftViewPayload(BlockPos pod, byte[] view, boolean open)
        implements CustomPacketPayload {

    /** An eight-drafter pod is far below this; a bound so a bad packet is refused. */
    public static final int MAX_BYTES = 1 << 18;

    public static final CustomPacketPayload.Type<DraftViewPayload> TYPE =
            GatheringPayloads.type("draft_view");

    public static final StreamCodec<RegistryFriendlyByteBuf, DraftViewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DraftViewPayload::pod,
                    ByteBufCodecs.byteArray(MAX_BYTES), DraftViewPayload::view,
                    ByteBufCodecs.BOOL, DraftViewPayload::open,
                    DraftViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
