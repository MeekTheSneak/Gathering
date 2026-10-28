package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: which cards this drafter is taking out of the pack in front of them.
 * <p>Places in the pack rather than card identities, and deliberately. A client naming the
 * cards it wants would be a client asserting what is in a pack, which is a client the server
 * would have to check against the pack anyway - so it says where instead, and the server
 * reads the cards off its own copy. It also means the packet carries no card identity at
 * all, so there is nothing in it worth intercepting.
 * <p>The pod is not asked who is picking: that comes from the connection. A client that
 * could name a drafter could empty a pack it may not read and learn what was in it from what
 * came back.
 * <p>And it says which turn it was made on, because a place in one pack is a different card in
 * the next. Without it, a pick that arrived after the packs had moved - Take pressed twice by the
 * drafter the pod was waiting on, or pressed as the pick clock ran out - was taken out of the next
 * pack, sight unseen. The server refuses one for a turn that has passed.
 *
 * @param turn the turn the drafter's screen was showing, as {@code PickClock.turnOf} has it
 */
public record DraftPickPayload(BlockPos pod, long turn, List<Integer> positions)
        implements AtATable {

    /**
     * A pod drafts at tables, so its position is a table's and answers the same question.
     * <p>Said here because {@code ClientNetworking.send} asks every serverbound payload where it is
     * going, to keep the guided first game - which is a board at a position no table occupies - off
     * the wire. This payload carried a table position under another name and was never asked.
     */
    @Override
    public BlockPos table() {
        return pod;
    }

    /** Nobody picks more than two at once; a bound so a bad packet is refused on arrival. */
    public static final int MAX_PICKS = 2;

    public static final CustomPacketPayload.Type<DraftPickPayload> TYPE =
            GatheringPayloads.type("draft_pick");

    public static final StreamCodec<RegistryFriendlyByteBuf, DraftPickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DraftPickPayload::pod,
                    ByteBufCodecs.VAR_LONG, DraftPickPayload::turn,
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
