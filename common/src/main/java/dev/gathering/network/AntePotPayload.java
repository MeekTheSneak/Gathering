package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what is in the pot at this table.
 *
 * <p>Sent on its own rather than folded into the board, and that is the point. The board is a
 * view built by the visibility rules for one particular pair of eyes; the pot is not, because
 * it is not in the game. Every card in it is face up to everybody in the room by definition -
 * that is what a pot is - so it can be sent to anyone who can see the table, and putting it
 * through the visibility path would mean adding something to that path which has no hidden
 * case at all.
 *
 * <p>Whose card was whose is deliberately not sent. It matters on the server, where it decides
 * who gets what back if the game is voided, and it is nobody's business at the table: a pot is
 * a pot.
 */
public record AntePotPayload(BlockPos table, List<CardComponent> cards)
        implements CustomPacketPayload {

    /** More cards than any table will stake, and a bound so a bad packet is refused. */
    public static final int MOST_CARDS = 64;

    public static final CustomPacketPayload.Type<AntePotPayload> TYPE =
            GatheringPayloads.type("ante_pot");

    public static final StreamCodec<RegistryFriendlyByteBuf, AntePotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AntePotPayload::table,
                    CardComponent.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CARDS)),
                    AntePotPayload::cards,
                    AntePotPayload::new);

    public AntePotPayload {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Nothing at stake here, which is also how a pot that has been paid out is announced. */
    public static AntePotPayload nothing(BlockPos table) {
        return new AntePotPayload(table, List.of());
    }
}
