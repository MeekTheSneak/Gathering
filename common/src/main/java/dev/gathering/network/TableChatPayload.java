package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: say this to the people at that table.
 *
 * <p>A game of Magic is half conversation - "attacking you with everything", "hold on, in
 * response", "how much life are you on" - and on a server the global chat is the wrong room
 * for it: everybody mining two hundred blocks away reads a turn they cannot see, and the
 * four people who need it lose it in the noise. So the table is its own room.
 *
 * <p>The text is cleaned by the server rather than trusted, by the same rule the pen follows -
 * one line, no formatting escapes - because it is the same thing: writing from one client
 * drawn on everybody else's screen. See {@link dev.gathering.core.game.PlayerText}.
 *
 * @param text what to say. Bounded on decode, so a bad client cannot make a packet out of it
 */
public record TableChatPayload(BlockPos table, String text) implements CustomPacketPayload {

    /**
     * How much may be said at once.
     *
     * <p>About two lines across a board. Long enough for anything anybody says during a turn
     * and short enough that the corner of the felt is not somebody's essay.
     */
    public static final int LONGEST = 160;

    public static final CustomPacketPayload.Type<TableChatPayload> TYPE =
            GatheringPayloads.type("table_chat");

    public static final StreamCodec<RegistryFriendlyByteBuf, TableChatPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TableChatPayload::table,
                    ByteBufCodecs.stringUtf8(LONGEST * 4), TableChatPayload::text,
                    TableChatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
