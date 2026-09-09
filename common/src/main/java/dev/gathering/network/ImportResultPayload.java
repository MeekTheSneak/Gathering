package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what happened to an import.
 * <p>Carries the problems verbatim rather than a yes or no, because the useful thing to do
 * with a decklist that half worked is to show the player which lines to fix, at their line
 * numbers, in their own words. An import with problems still produces a deck.
 *
 * @param deckName    the name the list carried, or empty
 * @param cardCount   physical cards resolved into the deck
 * @param problems    parse problems and unresolved lines, already formatted for display
 */
public record ImportResultPayload(String deckName, int cardCount, List<String> problems)
        implements CustomPacketPayload {

    /** Enough to name every line of a decklist that went entirely wrong, and no more. */
    public static final int MAX_PROBLEMS = 256;
    private static final int MAX_PROBLEM_LENGTH = 512;

    public static final CustomPacketPayload.Type<ImportResultPayload> TYPE =
            GatheringPayloads.type("import_result");

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ImportResultPayload::deckName,
                    ByteBufCodecs.VAR_INT, ImportResultPayload::cardCount,
                    ByteBufCodecs.stringUtf8(MAX_PROBLEM_LENGTH).apply(ByteBufCodecs.list(MAX_PROBLEMS)),
                            ImportResultPayload::problems,
                    ImportResultPayload::new);

    public ImportResultPayload {
        deckName = deckName == null ? "" : deckName;
        // Each line cut to what the codec writes. A problem quotes the line it is about, and
        // a decklist line can be longer than any problem may be; one over the bound refused
        // to encode and disconnected the player it was meant to help.
        List<String> fitted = new java.util.ArrayList<>(problems == null ? 0 : problems.size());
        if (problems != null) {
            for (String problem : problems) {
                String line = problem == null ? "" : problem;
                fitted.add(line.length() > MAX_PROBLEM_LENGTH ? line.substring(0, MAX_PROBLEM_LENGTH - 3) + "..." : line);
            }
        }
        problems = List.copyOf(fitted);
    }

    public boolean isClean() {
        return problems.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
