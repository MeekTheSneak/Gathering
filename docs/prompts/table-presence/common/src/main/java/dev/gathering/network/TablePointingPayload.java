package dev.gathering.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to everybody who can see the table: this player is pointing there.
 * <p>Carries the player rather than the seat, because the clients that receive it are drawing an
 * entity and the entity is what they have. The seat is on the board they already hold.
 * <p>Sent only to {@code TableBroadcast.watchingNearby}, which is the mod's one answer to "who
 * can see this table". A pointer sent further than that is a pointer nobody can use.
 *
 * @param player   whose arm this is
 * @param table    which table's felt the point is on
 * @param surfaceX where on it
 * @param surfaceY and how far down it
 * @param pointing false to put the arm back at rest
 */
public record TablePointingPayload(
        UUID player, BlockPos table, float surfaceX, float surfaceY, boolean pointing)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TablePointingPayload> TYPE =
            GatheringPayloads.type("table_pointing");

    public static final StreamCodec<RegistryFriendlyByteBuf, TablePointingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, TablePointingPayload::player,
                    BlockPos.STREAM_CODEC, TablePointingPayload::table,
                    ByteBufCodecs.FLOAT, TablePointingPayload::surfaceX,
                    ByteBufCodecs.FLOAT, TablePointingPayload::surfaceY,
                    ByteBufCodecs.BOOL, TablePointingPayload::pointing,
                    TablePointingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
