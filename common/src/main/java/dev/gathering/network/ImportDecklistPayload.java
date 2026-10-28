package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "here is a decklist I pasted, make me a deck".
 * <p>The text is capped well above any real decklist and well below anything worth
 * worrying about. Parsing and resolution happen on the server, on the card pipeline's own
 * executor, and the client is told what came of it - it never resolves anything itself.
 * <p>Every request names itself, and the answer comes back carrying that name. A screen that
 * is waiting acts on the answer to its own press and on nothing else: an import started from
 * one screen and answered after the player has opened another used to close whatever screen
 * was open, because "an answer arrived and I am waiting" was taken for "this is my answer".
 * <p>With a collection named, the same list builds a deck out of that collection's cards
 * instead of out of nothing - which is the same request with the cards having to come from
 * somewhere, rather than a second kind of import. The position is checked at the other end
 * like every other position a client names: reading a collection is public, and being in
 * front of one is not.
 */
public record ImportDecklistPayload(
        String decklist, String deckName, String description,
        java.util.Optional<net.minecraft.core.BlockPos> from, java.util.UUID forRequest)
        implements CustomPacketPayload {

    /** A 100-card Commander list with printing hints runs to a few kilobytes. */
    public static final int MAX_LENGTH = 64 * 1024;

    public static final CustomPacketPayload.Type<ImportDecklistPayload> TYPE =
            GatheringPayloads.type("import_decklist");

    /** A title and a note, both short; the decklist itself is the only long field. */
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportDecklistPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_LENGTH), ImportDecklistPayload::decklist,
                    ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), ImportDecklistPayload::deckName,
                    ByteBufCodecs.stringUtf8(MAX_DESCRIPTION_LENGTH), ImportDecklistPayload::description,
                    ByteBufCodecs.optional(net.minecraft.core.BlockPos.STREAM_CODEC),
                    ImportDecklistPayload::from,
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, ImportDecklistPayload::forRequest,
                    ImportDecklistPayload::new);

    /** Out of nothing, which is what import mode does. */
    public ImportDecklistPayload(String decklist, String deckName, String description) {
        this(decklist, deckName, description, java.util.Optional.empty(), java.util.UUID.randomUUID());
    }

    /** Out of a collection, named so the answer can be told from somebody else's. */
    public ImportDecklistPayload(String decklist, String deckName, String description,
            java.util.Optional<net.minecraft.core.BlockPos> from) {
        this(decklist, deckName, description, from, java.util.UUID.randomUUID());
    }

    public ImportDecklistPayload {
        forRequest = forRequest == null ? java.util.UUID.randomUUID() : forRequest;
        deckName = trimTo(deckName, MAX_NAME_LENGTH);
        description = trimTo(description, MAX_DESCRIPTION_LENGTH);
        from = from == null ? java.util.Optional.empty() : from;
    }

    private static String trimTo(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
