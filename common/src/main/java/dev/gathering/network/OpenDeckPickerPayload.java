package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: your seat at this game has no deck down, so choose one.
 * <p>Carries no decks. They are the ones in the player's own inventory, which their client already has;
 * what comes back is a slot, and the server reads the deck out of that slot itself.
 *
 * @param loaners whether this server lends decks, so the choice can offer borrowing one
 */
public record OpenDeckPickerPayload(BlockPos table, boolean loaners) implements AtATable {

    public static final CustomPacketPayload.Type<OpenDeckPickerPayload> TYPE =
            GatheringPayloads.type("open_deck_picker");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDeckPickerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenDeckPickerPayload::table,
                    ByteBufCodecs.BOOL, OpenDeckPickerPayload::loaners,
                    OpenDeckPickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
