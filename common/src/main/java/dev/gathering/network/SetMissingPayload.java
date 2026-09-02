package dev.gathering.network;

import dev.gathering.core.card.Rarity;
import dev.gathering.core.collection.MissingCards;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the cards of one set this collection has not got.
 * <p>The list behind the number. Names and numbers rather than card details: the art comes
 * the way art always comes, asked for by the screen showing it as it scrolls, so opening a
 * three-hundred-card list is not three hundred pictures nobody looked at.
 */
public record SetMissingPayload(String code, String name, List<Row> cards, int missing)
        implements CustomPacketPayload {

    /**
     * How many rows are worth sending.
     * <p>Comfortably past the largest set anybody has printed, so in practice nothing is ever
     * cut - it is here because a list off the wire needs a bound, not because a set is
     * expected to reach it. {@link #missing} carries the real total either way, so a list
     * that was cut still says how many there are.
     */
    public static final int MOST_CARDS = 1024;

    public static final int LONGEST_NAME = 128;

    /** One card still to find. */
    public record Row(int number, String name, Rarity rarity, UUID printing) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Row::number,
                        ByteBufCodecs.stringUtf8(LONGEST_NAME), Row::name,
                        CardSummary.RARITY_STREAM_CODEC, Row::rarity,
                        UUIDUtil.STREAM_CODEC, Row::printing,
                        Row::new);

        public static Row of(MissingCards.Card card) {
            return new Row(card.number(), card.name(), card.rarity(), card.printing());
        }

        public MissingCards.Card asCard() {
            return new MissingCards.Card(number, name, rarity, printing);
        }
    }

    /** Everything found, trimmed to what the wire will carry. */
    public static SetMissingPayload of(MissingCards missing) {
        List<Row> rows = new ArrayList<>(Math.min(missing.count(), MOST_CARDS));
        for (MissingCards.Card card : missing.cards()) {
            if (rows.size() == MOST_CARDS) {
                break;
            }
            rows.add(Row.of(card));
        }
        return new SetMissingPayload(missing.code(), missing.name(), rows, missing.count());
    }

    /** Back into the shape the screen reads, so the client does no arithmetic. */
    public MissingCards asMissing() {
        List<MissingCards.Card> found = new ArrayList<>(cards.size());
        for (Row row : cards) {
            found.add(row.asCard());
        }
        return new MissingCards(code, name, found);
    }

    public static final CustomPacketPayload.Type<SetMissingPayload> TYPE =
            GatheringPayloads.type("set_missing");

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMissingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(SetProgressPayload.LONGEST_CODE),
                    SetMissingPayload::code,
                    ByteBufCodecs.stringUtf8(SetProgressPayload.LONGEST_NAME),
                    SetMissingPayload::name,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CARDS)),
                    SetMissingPayload::cards,
                    ByteBufCodecs.VAR_INT, SetMissingPayload::missing,
                    SetMissingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
