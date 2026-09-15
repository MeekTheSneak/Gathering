package dev.gathering.network;

import dev.gathering.core.match.TableTerms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what this table is playing - format, match length, which game, and whether
 * for keeps.
 * <p>On its own rather than inside the board, for the reason the pot is: the board is built by
 * the visibility rules for one pair of eyes, and nothing here has a hidden case. Everybody at the
 * table agreed to all of it out loud. Sent with every board, because it is a few bytes beside one
 * and a separate lifecycle would be one more thing to be out of date.
 */
public record TableTermsPayload(BlockPos table, TableTerms terms) implements AtATable {

    /** Longer than any preset's id, and short enough that a made-up one costs nothing. */
    private static final int LONGEST_ID = 64;

    public static final CustomPacketPayload.Type<TableTermsPayload> TYPE = GatheringPayloads.type("table_terms");

    private static final StreamCodec<RegistryFriendlyByteBuf, TableTerms> TERMS = StreamCodec.of(
            (buffer, terms) -> {
                ByteBufCodecs.stringUtf8(LONGEST_ID).encode(buffer, terms.formatId());
                buffer.writeVarInt(terms.bestOf());
                buffer.writeVarInt(terms.gameNumber());
                buffer.writeBoolean(terms.freePlay());
                buffer.writeBoolean(terms.forKeeps());
                buffer.writeBoolean(terms.practice());
                buffer.writeVarInt(terms.eventTable());
            },
            buffer -> new TableTerms(ByteBufCodecs.stringUtf8(LONGEST_ID).decode(buffer),
                    // Bounded as they are read: a length a client would draw as "best of a million".
                    Math.min(buffer.readVarInt(), 99), Math.min(buffer.readVarInt(), 99),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    Math.min(buffer.readVarInt(), 9_999)));

    public static final StreamCodec<RegistryFriendlyByteBuf, TableTermsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TableTermsPayload::table,
            TERMS, TableTermsPayload::terms,
            TableTermsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
