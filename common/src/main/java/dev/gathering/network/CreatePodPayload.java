package dev.gathering.network;

import dev.gathering.core.draft.PodSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: open a draft or sealed signup at this table with these settings. */
public record CreatePodPayload(BlockPos table, PodSettings settings) implements AtATable {

    public static final CustomPacketPayload.Type<CreatePodPayload> TYPE = GatheringPayloads.type("create_pod");

    public static final StreamCodec<RegistryFriendlyByteBuf, CreatePodPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CreatePodPayload::table,
                    PodWire.SETTINGS.cast(), CreatePodPayload::settings,
                    CreatePodPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
