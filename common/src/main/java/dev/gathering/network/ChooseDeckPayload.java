package dev.gathering.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: put down the deck in this slot of my inventory at my seat here, or borrow one.
 * <p>A slot, never a deck: the server reads what is in the slot, so nothing a client says can put a card
 * on the table that the player is not carrying.
 *
 * @param slot    the inventory slot, or {@link #BORROW} to be offered the server's loaner decks
 * @param anyway  whether the player has been told what is not legal about this deck in the table's
 *                format and chose to play it anyway
 */
public record ChooseDeckPayload(
        BlockPos table, int slot, boolean anyway, java.util.Optional<java.util.UUID> deck)
        implements AtATable {

    /** Which deck the player meant, as its handle, for a slot whose contents may have moved on. */
    public static ChooseDeckPayload of(BlockPos table, int slot, boolean anyway,
            net.minecraft.world.item.ItemStack stack) {
        return new ChooseDeckPayload(table, slot, anyway,
                stack == null ? java.util.Optional.empty() : dev.gathering.item.DeckItem.handleOf(stack));
    }

    /** Not a slot: the loaner decks, please. */
    public static final int BORROW = -1;

    public static final CustomPacketPayload.Type<ChooseDeckPayload> TYPE =
            GatheringPayloads.type("choose_deck");

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseDeckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ChooseDeckPayload::table,
                    ByteBufCodecs.VAR_INT, ChooseDeckPayload::slot,
                    ByteBufCodecs.BOOL, ChooseDeckPayload::anyway,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC),
                    ChooseDeckPayload::deck,
                    ChooseDeckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
