package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player put the cards in these slots into the deck on their cursor - swept over
 * them with the right button held, or right-clicked one by one.
 * <p>Asks; the server decides. It does the same thing a right-click of the deck on each slot does, on
 * its own stacks - which is what makes this safe in the creative inventory, where the client's copy of
 * a deck has its cards hidden and the client is otherwise trusted with what it sends back.
 *
 * @param containerId the menu the slots belong to, as the client has it open
 * @param deck        which deck is on the cursor, by its handle, for the creative inventory - it keeps
 *                    the cursor to itself, so the server has no other way to know which deck this is.
 *                    Empty everywhere else, where the server holds the cursor itself and is believed
 *                    over anything a client says
 * @param slots       the slot numbers in that menu, in the order the cursor crossed them
 */
public record DeckSweepPayload(int containerId, java.util.Optional<java.util.UUID> deck, List<Integer> slots)
        implements CustomPacketPayload {

    /** The most slots one sweep names. A row of an inventory is nine; a whole one is thirty-six. */
    public static final int MOST_SLOTS = 64;

    public static final CustomPacketPayload.Type<DeckSweepPayload> TYPE = GatheringPayloads.type("deck_sweep");

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckSweepPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DeckSweepPayload::containerId,
                    ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), DeckSweepPayload::deck,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST_SLOTS)), DeckSweepPayload::slots,
                    DeckSweepPayload::new);

    public DeckSweepPayload {
        deck = deck == null ? java.util.Optional.empty() : deck;
        slots = slots == null ? List.of() : List.copyOf(slots.subList(0, Math.min(slots.size(), MOST_SLOTS)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
