package dev.gathering.network;

import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.PrizeOffer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: host a tournament at this Scorekeeper's Desk, played at the free tables near it.
 *
 * @param prizes what the host put up on the create screen, as places and the hotbar slots holding
 *               them. The items themselves are read out of the host's own inventory on the server,
 *               so this names property rather than carrying it - see {@link PrizeOffer}. Never more
 *               than a hotbar's worth, because a slot is at most one prize.
 */
public record CreateEventPayload(BlockPos desk, String name, EventSettings settings, List<PrizeOffer> prizes)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateEventPayload> TYPE = GatheringPayloads.type("create_event");

    private static final StreamCodec<RegistryFriendlyByteBuf, PrizeOffer> PRIZE = StreamCodec.of(
            (buffer, offer) -> {
                buffer.writeVarInt(offer.place());
                buffer.writeVarInt(offer.slot());
            },
            buffer -> new PrizeOffer(buffer.readVarInt(), buffer.readVarInt()));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateEventPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CreateEventPayload::desk,
                    ByteBufCodecs.stringUtf8(40), CreateEventPayload::name,
                    EventWire.SETTINGS.cast(), CreateEventPayload::settings,
                    PRIZE.apply(ByteBufCodecs.list(PrizeOffer.HOTBAR_SLOTS)), CreateEventPayload::prizes,
                    CreateEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
