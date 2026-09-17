package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player swept a deck over these slots with the right button held, so put the
 * card in each one into the deck they are carrying.
 * <p>Asks; the server decides. It does the same thing a right-click of the deck on each slot does, on
 * its own stacks - which is what makes this safe in the creative inventory, where the client's copy of
 * a deck has its cards hidden and the client is otherwise trusted with what it sends back.
 *
 * @param containerId the menu the slots belong to, as the client has it open
 * @param slots       the slot numbers in that menu, in the order the cursor crossed them
 */
public record DeckSweepPayload(int containerId, List<Integer> slots) implements CustomPacketPayload {

    /** The most slots one sweep names. A row of an inventory is nine; a whole one is thirty-six. */
    public static final int MOST_SLOTS = 64;

    public static final CustomPacketPayload.Type<DeckSweepPayload> TYPE = GatheringPayloads.type("deck_sweep");

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckSweepPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DeckSweepPayload::containerId,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST_SLOTS)), DeckSweepPayload::slots,
                    DeckSweepPayload::new);

    public DeckSweepPayload {
        slots = slots == null ? List.of() : List.copyOf(slots.subList(0, Math.min(slots.size(), MOST_SLOTS)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
