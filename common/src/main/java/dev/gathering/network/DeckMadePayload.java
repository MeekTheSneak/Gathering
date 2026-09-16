package dev.gathering.network;

import dev.gathering.item.DeckComponent;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "the deck I just made out of two cards holds these".
 * <p>Only ever sent from the creative menu, and only believed from a player who is in creative.
 *
 * <p><b>Why this has to exist.</b> Right-clicking one card onto another makes a deck, and in an
 * ordinary inventory the server runs that click itself and builds the deck from its own cards. The
 * creative menu does not work that way: it never replays the click at all, it sends the resulting
 * <em>stack</em>. And a deck component has one wire format, which replaces every card in it with a
 * stand-in so that carrying a deck past somebody does not hand them your list - in both directions.
 * So the deck arrived at the server with its cards already gone, under a handle nothing had ever
 * seen, and there was no copy of the real list anywhere to put back. Every row of it read as a card
 * still loading, and taking one out gave back nothing at all. The owner reported it three times.
 *
 * <p><b>Why it is safe to believe.</b> It is refused unless the sender is in creative, and a player
 * in creative can already conjure any card in the game from the menu they are standing in. It grants
 * nothing that was not already theirs. A player who is not in creative is refused and does not need
 * it: for them the server runs the click and this never arrives.
 *
 * <p>The contents go no further than {@link dev.gathering.server.DeckVault}, which is where the
 * server already looks to put a redacted deck back together - so the repair happens on the path that
 * already existed, rather than as a second way for a deck to be made.
 *
 * @param handle which deck this is about, minted by the client when it built the stack and carried
 *     on the item beside the deck, where the redaction does not reach
 */
public record DeckMadePayload(UUID handle, DeckComponent deck) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeckMadePayload> TYPE =
            GatheringPayloads.type("deck_made");

    /**
     * The full format, not the public one.
     * <p>The whole point of this message is to carry the cards the public format leaves out. It goes
     * one way, from the player who is holding the deck to the server that is about to own it, so
     * nobody learns anything they did not already have in their hand.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DeckMadePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, DeckMadePayload::handle,
                    DeckComponent.STREAM_CODEC, DeckMadePayload::deck,
                    DeckMadePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
