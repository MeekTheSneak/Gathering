package dev.gathering.network;

import dev.gathering.core.loaner.LoanerShelf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: lend me that one.
 *
 * <p>The name is the one the server just sent, and the server looks it up against its own
 * shelf rather than doing anything with the string - so a client asking for a deck that is
 * not on the shelf gets nothing rather than an error worth exploring.
 */
public record TakeLoanerPayload(BlockPos table, String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TakeLoanerPayload> TYPE =
            GatheringPayloads.type("take_loaner");

    public static final StreamCodec<RegistryFriendlyByteBuf, TakeLoanerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TakeLoanerPayload::table,
                    ByteBufCodecs.stringUtf8(LoanerShelf.MOST_NAME_CHARACTERS),
                    TakeLoanerPayload::name,
                    TakeLoanerPayload::new);

    public TakeLoanerPayload {
        name = name == null ? "" : name;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
