package dev.gathering.network;

import dev.gathering.core.tournament.EventSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: host a tournament at the long table this table is part of. */
public record CreateEventPayload(BlockPos table, String name, EventSettings settings) implements AtATable {

    public static final CustomPacketPayload.Type<CreateEventPayload> TYPE = GatheringPayloads.type("create_event");

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateEventPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CreateEventPayload::table,
                    ByteBufCodecs.stringUtf8(40), CreateEventPayload::name,
                    EventWire.SETTINGS.cast(), CreateEventPayload::settings,
                    CreateEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
