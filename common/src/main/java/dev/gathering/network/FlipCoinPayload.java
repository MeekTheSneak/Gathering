package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: flip a coin where everyone can see it.
 *
 * <p>Its own verb rather than a two-sided die, because Magic's own words are heads and tails.
 * A card says "flip a coin" and Krark's Thumb cares which one you did, so a log line reading
 * "rolled a 1" would be true and useless.
 *
 * <p>The side is the server's to decide, for the same reason a die's number is.
 */
public record FlipCoinPayload(BlockPos table) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FlipCoinPayload> TYPE =
            GatheringPayloads.type("flip_coin");

    // The one-field composite rather than BlockPos.STREAM_CODEC.map: map keeps the buffer
    // type it was given, and BlockPos's codec is typed to a plain ByteBuf, which is not a
    // RegistryFriendlyByteBuf however much it looks like one from here.
    public static final StreamCodec<RegistryFriendlyByteBuf, FlipCoinPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FlipCoinPayload::table,
                    FlipCoinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
