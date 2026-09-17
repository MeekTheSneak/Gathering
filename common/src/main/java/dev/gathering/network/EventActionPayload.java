package dev.gathering.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: something done in a tournament.
 * <p>Who is doing it is the connection. What a player may do to an event - report their own
 * match, drop themselves - and what only its host may - settle a table, drop somebody, start -
 * is decided on the server from that, never from anything written here.
 *
 * @param event  the tournament, or the nil UUID for the list
 * @param action what is being done
 * @param tableNumber  a table number, for settling; or a place, for a prize
 * @param winsA  games won by the first player (or by the reporter, for a report)
 * @param winsB  games won by the second player (or by the reporter's opponent)
 * @param draws  games drawn
 * @param player a player the host is acting on, or the nil UUID
 * @param at     the table the player is standing at, for adding tables
 */
public record EventActionPayload(
        UUID event, Action action, int tableNumber, int winsA, int winsB, int draws, UUID player, BlockPos at)
        implements AtATable {

    /**
     * Where the host is standing, which for ADD_TABLES is the table they mean.
     * <p>Said here because {@code ClientNetworking.send} asks every serverbound payload where it is
     * going, to keep the guided first game off the wire. This one carried a position called
     * {@code at} and was never asked. Null where the action is not about a place, which the guard
     * reads as nowhere in particular rather than as the demonstration.
     */
    @Override
    public BlockPos table() {
        return at;
    }

    public enum Action {
        LIST, VIEW, REGISTER, WITHDRAW, CHECK_IN, READY, REPORT, OPEN_CHECK_IN, BEGIN, START_NOW, SETTLE,
        DROP_PLAYER, CANCEL, ADD_TABLES, ADD_PRIZE, RECORD, MARK_REGISTRATION
    }

    public static final UUID NONE = new UUID(0L, 0L);

    public static EventActionPayload of(UUID event, Action action) {
        return new EventActionPayload(event, action, 0, 0, 0, 0, NONE, BlockPos.ZERO);
    }

    public static final CustomPacketPayload.Type<EventActionPayload> TYPE = GatheringPayloads.type("event_action");

    public static final StreamCodec<RegistryFriendlyByteBuf, EventActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.event());
                buffer.writeVarInt(payload.action().ordinal());
                buffer.writeVarInt(payload.tableNumber());
                buffer.writeVarInt(payload.winsA());
                buffer.writeVarInt(payload.winsB());
                buffer.writeVarInt(payload.draws());
                buffer.writeUUID(payload.player());
                BlockPos.STREAM_CODEC.encode(buffer, payload.at());
            },
            buffer -> new EventActionPayload(buffer.readUUID(), EventWire.pick(Action.values(), buffer.readVarInt()),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUUID(),
                    BlockPos.STREAM_CODEC.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
