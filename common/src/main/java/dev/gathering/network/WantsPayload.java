package dev.gathering.network;

import dev.gathering.core.collection.WantsList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: everything this player is chasing.
 *
 * <p>The whole list every time rather than what changed. It is a few thousand ids at the very
 * most and it changes when somebody presses a button, so the saving is nothing and the cost of
 * getting it wrong - a client and a server disagreeing about what somebody wants - is a
 * marker on the wrong card that nothing would ever correct.
 */
public record WantsPayload(List<UUID> printings) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WantsPayload> TYPE =
            GatheringPayloads.type("wants");

    public static final StreamCodec<RegistryFriendlyByteBuf, WantsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(WantsList.MOST)),
                    WantsPayload::printings,
                    WantsPayload::new);

    public static WantsPayload of(WantsList wants) {
        return new WantsPayload(wants.printings());
    }

    public WantsList asWants() {
        return new WantsList(printings);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
