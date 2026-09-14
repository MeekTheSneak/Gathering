package dev.gathering.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the tournaments on this server, running and recently finished. */
public record EventListPayload(List<Summary> events, boolean show) implements CustomPacketPayload {

    /**
     * @param phase   a phase key
     * @param kind    a kind key
     * @param format  a display name, blank for limited
     * @param players how many are registered
     * @param yours   whether the player this is sent to is registered
     * @param hosting whether they host it
     */
    public record Summary(UUID id, String name, String host, String phase, String kind, String format, int players,
            boolean yours, boolean hosting) {

        static final StreamCodec<RegistryFriendlyByteBuf, Summary> CODEC = StreamCodec.of(
                (buffer, summary) -> {
                    buffer.writeUUID(summary.id());
                    buffer.writeUtf(summary.name(), 64);
                    buffer.writeUtf(summary.host(), 64);
                    buffer.writeUtf(summary.phase(), 32);
                    buffer.writeUtf(summary.kind(), 32);
                    buffer.writeUtf(summary.format(), 64);
                    buffer.writeVarInt(summary.players());
                    buffer.writeBoolean(summary.yours());
                    buffer.writeBoolean(summary.hosting());
                },
                buffer -> new Summary(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32),
                        buffer.readUtf(32), buffer.readUtf(64), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()));
    }

    public static final int MOST = 64;

    public static final CustomPacketPayload.Type<EventListPayload> TYPE = GatheringPayloads.type("event_list");

    public static final StreamCodec<RegistryFriendlyByteBuf, EventListPayload> STREAM_CODEC = StreamCodec.composite(
            Summary.CODEC.apply(ByteBufCodecs.list(MOST)), EventListPayload::events,
            ByteBufCodecs.BOOL, EventListPayload::show,
            EventListPayload::new);

    public EventListPayload {
        events = List.copyOf(events);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
