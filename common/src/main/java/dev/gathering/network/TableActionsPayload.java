package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "I did each of these at that table, in this order."
 * <p>What a verb on a selection sends. It used to be one {@link TableActionPayload} per card, and
 * each of those was answered with a fresh board for every person who could see the table: forty
 * cards tapped at a table of six was two hundred and forty boards built, serialized and sent, of
 * which the last six were the only ones anybody needed.
 * <p><b>Nothing about what is allowed changes.</b> Every event in here goes through exactly the
 * gates a single one does, one at a time and in order - unreadable, not seated, server-authored,
 * signed with somebody else's seat, refused by the rules - and is refused or applied on its own.
 * The only difference is that the board goes out once, after the last of them, rather than after
 * each. A selection still cannot do anything a sequence of single moves could not.
 * <p>Bounded twice: by count, at {@link dev.gathering.core.ui.BulkLimit#MOST_AT_ONCE}, and by the
 * size of each event, at {@link #MOST_BYTES_EACH}. A payload over either does not decode.
 */
public record TableActionsPayload(BlockPos table, List<byte[]> events) implements AtATable {

    /**
     * The most one event in a batch may take.
     * <p>Far below a single action's allowance, because a single action can be a deck load and a
     * batch is a verb applied to a selection: a tap, a move, a counter - tens of bytes each.
     * <p>These bounds cap what the server will decode and do; they do <b>not</b> make every
     * payload that meets them sendable. At the limits this codec accepts about 66 KB, and a
     * client may send at most 32,767 bytes in one packet. Keeping a batch under that is
     * {@code ClientTableActions.sendAll}'s job - it splits at 16 KB - so build these through it
     * rather than directly.
     */
    public static final int MOST_BYTES_EACH = 512;

    public TableActionsPayload {
        events = List.copyOf(events);
    }

    public static final CustomPacketPayload.Type<TableActionsPayload> TYPE =
            GatheringPayloads.type("table_actions");

    public static final StreamCodec<RegistryFriendlyByteBuf, TableActionsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TableActionsPayload::table,
                    ByteBufCodecs.byteArray(MOST_BYTES_EACH).apply(
                            ByteBufCodecs.list(dev.gathering.core.ui.BulkLimit.MOST_AT_ONCE)),
                    TableActionsPayload::events,
                    TableActionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
