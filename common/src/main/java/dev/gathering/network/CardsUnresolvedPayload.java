package dev.gathering.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: printings the client asked about that the server has no name for, and why.
 * <p>The other half of {@link CardMetadataPayload}. A lookup that found nothing, or could not
 * be made, used to send nothing at all - and a client that is sent nothing cannot tell "still
 * looking" from "never going to know", so it said "Loading" for ever.
 * <p>Only ever about printings this client asked about in a {@link RequestCardMetadataPayload},
 * so it tells the client nothing it had not already named itself.
 *
 * @param missing     looked up, and nothing is called that
 * @param unavailable could not be looked up just now; worth asking again later
 */
public record CardsUnresolvedPayload(List<UUID> missing, List<UUID> unavailable)
        implements CustomPacketPayload {

    /** No more than one request could name, per list. */
    public static final int MOST_PER_LIST = RequestCardMetadataPayload.MAX_REQUESTED;

    public static final CustomPacketPayload.Type<CardsUnresolvedPayload> TYPE =
            GatheringPayloads.type("cards_unresolved");

    public static final StreamCodec<RegistryFriendlyByteBuf, CardsUnresolvedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_PER_LIST)),
                    CardsUnresolvedPayload::missing,
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_PER_LIST)),
                    CardsUnresolvedPayload::unavailable,
                    CardsUnresolvedPayload::new);

    public CardsUnresolvedPayload {
        missing = missing == null ? List.of() : List.copyOf(missing);
        unavailable = unavailable == null ? List.of() : List.copyOf(unavailable);
    }

    public boolean isEmpty() {
        return missing.isEmpty() && unavailable.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
