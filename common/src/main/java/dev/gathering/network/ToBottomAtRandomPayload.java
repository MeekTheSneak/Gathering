package dev.gathering.network;

import dev.gathering.core.game.CardInstanceId;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: put these under the library, and do not let me choose the order.
 * <p>The cards are named here, unlike a random discard - they are the ones the player is
 * pointing at, and everybody can see which. What the client must not decide is the order they
 * go back in, because that order is a fact about the bottom of a library and nobody is
 * entitled to know it, least of all the person who just put them there.
 * <p>A client that shuffled them itself would look exactly like one that did not, and the
 * player would have a library whose last four cards they could name. So the shuffling is the
 * server's, from the level's own randomness and never from the session's shuffle seed.
 */
public record ToBottomAtRandomPayload(BlockPos table, List<CardInstanceId> cards)
        implements CustomPacketPayload {

    /**
     * The most cards one press may put back.
     * <p>A selection is however many somebody dragged a box round, so this is what stops a
     * misdrag turning into every permanent on the table going under the library.
     */
    public static final int MOST = 64;

    public ToBottomAtRandomPayload {
        cards = cards == null ? List.of() : List.copyOf(cards.subList(0, Math.min(cards.size(), MOST)));
    }

    public static final CustomPacketPayload.Type<ToBottomAtRandomPayload> TYPE =
            GatheringPayloads.type("to_bottom_at_random");

    private static final StreamCodec<io.netty.buffer.ByteBuf, CardInstanceId> CARD =
            ByteBufCodecs.VAR_INT.map(CardInstanceId::new, CardInstanceId::value);

    public static final StreamCodec<RegistryFriendlyByteBuf, ToBottomAtRandomPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ToBottomAtRandomPayload::table,
                    CARD.apply(ByteBufCodecs.list(MOST)), ToBottomAtRandomPayload::cards,
                    ToBottomAtRandomPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
