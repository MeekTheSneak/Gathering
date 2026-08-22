package dev.gathering.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "here is a decklist I pasted, make me a deck".
 *
 * <p>The text is capped well above any real decklist and well below anything worth
 * worrying about. Parsing and resolution happen on the server, on the card pipeline's own
 * executor, and the client is told what came of it - it never resolves anything itself.
 */
public record ImportDecklistPayload(String decklist, String deckName, String description)
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
                    ImportDecklistPayload::new);

    public ImportDecklistPayload {
        deckName = trimTo(deckName, MAX_NAME_LENGTH);
        description = trimTo(description, MAX_DESCRIPTION_LENGTH);
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
