package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the deck in this slot is not legal in the table's format, and this is why - use it
 * anyway?
 * <p>The answer is the same choice again with {@link ChooseDeckPayload#anyway()} set, so the deck is read
 * out of the slot and checked a second time rather than trusted from the first.
 *
 * @param more how many problems there were beyond the ones listed
 */
public record DeckNotLegalPayload(BlockPos table, int slot, String deck, String format, List<String> problems, int more)
        implements AtATable {

    /** The most problems listed. */
    public static final int MOST = 8;

    public static final CustomPacketPayload.Type<DeckNotLegalPayload> TYPE =
            GatheringPayloads.type("deck_not_legal");

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckNotLegalPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DeckNotLegalPayload::table,
                    ByteBufCodecs.VAR_INT, DeckNotLegalPayload::slot,
                    ByteBufCodecs.stringUtf8(256), DeckNotLegalPayload::deck,
                    ByteBufCodecs.stringUtf8(64), DeckNotLegalPayload::format,
                    ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(MOST)), DeckNotLegalPayload::problems,
                    ByteBufCodecs.VAR_INT, DeckNotLegalPayload::more,
                    DeckNotLegalPayload::new);

    public DeckNotLegalPayload {
        problems = List.copyOf(problems);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
