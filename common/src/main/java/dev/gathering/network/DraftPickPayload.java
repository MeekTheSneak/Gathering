package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: which cards this drafter is taking out of the pack in front of them.
 *
 * <p>Places in the pack rather than card identities, and deliberately. A client naming the
 * cards it wants would be a client asserting what is in a pack, which is a client the server
 * would have to check against the pack anyway - so it says where instead, and the server
 * reads the cards off its own copy. It also means the packet carries no card identity at
 * all, so there is nothing in it worth intercepting.
 *
 * <p>The pod is not asked who is picking: that comes from the connection. A client that
 * could name a drafter could empty a pack it may not read and learn what was in it from what
 * came back.
 */
public record DraftPickPayload(BlockPos pod, List<Integer> positions)
        implements CustomPacketPayload {

    /** Nobody picks more than two at once; a bound so a bad packet is refused on arrival. */
    public static final int MAX_PICKS = 2;

    public static final CustomPacketPayload.Type<DraftPickPayload> TYPE =
            GatheringPayloads.type("draft_pick");

    public static final StreamCodec<RegistryFriendlyByteBuf, DraftPickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DraftPickPayload::pod,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_PICKS)),
                    DraftPickPayload::positions,
                    DraftPickPayload::new);

    public DraftPickPayload {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
