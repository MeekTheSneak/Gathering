package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: roll the planar die where everyone can see it.
 *
 * <p>Nothing to carry but the table. Which face comes up is the server's, for the same reason
 * a coin's side is: the whole value of rolling at this table rather than on a desk is that
 * everybody watched the same symbol come up, and a player who rolled their own would be
 * claiming a chaos symbol rather than getting one.
 */
public record RollPlanarPayload(BlockPos table) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RollPlanarPayload> TYPE =
            GatheringPayloads.type("roll_planar");

    // The one-field composite, for the reason FlipCoinPayload spells out.
    public static final StreamCodec<RegistryFriendlyByteBuf, RollPlanarPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RollPlanarPayload::table,
                    RollPlanarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
