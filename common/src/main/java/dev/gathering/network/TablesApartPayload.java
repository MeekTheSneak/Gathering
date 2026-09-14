package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: play the long table this table is part of apart, or together. */
public record TablesApartPayload(BlockPos table, boolean apart) implements AtATable {

    public static final CustomPacketPayload.Type<TablesApartPayload> TYPE = GatheringPayloads.type("tables_apart");

    public static final StreamCodec<RegistryFriendlyByteBuf, TablesApartPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TablesApartPayload::table,
                    ByteBufCodecs.BOOL, TablesApartPayload::apart,
                    TablesApartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
