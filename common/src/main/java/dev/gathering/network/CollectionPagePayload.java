package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one page of a collection, and what is on it.
 *
 * <p>Each row carries its own card details, so the screen can draw the page the moment it
 * lands rather than asking again for forty names. A row whose details the server has not got
 * yet still comes - the card is owned and the count is true - and the screen draws what it
 * can.
 */
public record CollectionPagePayload(
        BlockPos where, int page, int pages, CollectionPagePayload.Counts counts,
        List<CollectionPagePayload.Row> rows)
        implements CustomPacketPayload {

    /** As many rows as a page may hold, however tall the window asking is. */
    public static final int ROWS_PER_PAGE = 160;

    /**
     * How big the collection is and how much of it this search found.
     *
     * <p>Sent with every page rather than once when the screen opens, because taking a card
     * out changes it: a header still saying forty-four cards after four have left is a screen
     * lying about the thing it is showing.
     */
    public record Counts(int total, int distinct, int matched) {

        public static final Counts NOTHING = new Counts(0, 0, 0);

        public static final StreamCodec<RegistryFriendlyByteBuf, Counts> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Counts::total,
                        ByteBufCodecs.VAR_INT, Counts::distinct,
                        ByteBufCodecs.VAR_INT, Counts::matched,
                        Counts::new);
    }

    /**
     * One line: a card, how many, and what is known about it.
     *
     * @param about the card's details, absent where the server has not looked it up yet
     */
    public record Row(CardComponent card, int count, java.util.Optional<CardSummary> about) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        CardComponent.STREAM_CODEC, Row::card,
                        ByteBufCodecs.VAR_INT, Row::count,
                        ByteBufCodecs.optional(CardSummary.STREAM_CODEC), Row::about,
                        Row::new);
    }

    public static final CustomPacketPayload.Type<CollectionPagePayload> TYPE =
            GatheringPayloads.type("collection_page");

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionPagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CollectionPagePayload::where,
                    ByteBufCodecs.VAR_INT, CollectionPagePayload::page,
                    ByteBufCodecs.VAR_INT, CollectionPagePayload::pages,
                    Counts.STREAM_CODEC, CollectionPagePayload::counts,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list(ROWS_PER_PAGE)),
                    CollectionPagePayload::rows,
                    CollectionPagePayload::new);

    public CollectionPagePayload {
        counts = counts == null ? Counts.NOTHING : counts;
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
