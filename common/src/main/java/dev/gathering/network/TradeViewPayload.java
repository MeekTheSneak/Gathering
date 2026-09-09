package dev.gathering.network;

import dev.gathering.item.CardComponent;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the trade table as this person sees it.
 * <p>Sent whole rather than as changes, and sent to both people every time either of them
 * touches anything. A trade is two people looking at the same thing and agreeing to it, so
 * "the same thing" has to be a fact the server states rather than something two clients each
 * work out - a screen that had assembled its own view from a stream of edits would be a
 * screen that could disagree with the other one about what was being agreed to.
 * <p>Named from the reader's side. What is mine and what is theirs, rather than left and
 * right, because the two people are looking at the same table from opposite sides of it.
 *
 * @param mine       what this person has put up
 * @param theirs     what the other has
 * @param iAgreed    whether this person has said yes to the table as it stands
 * @param theyAgreed the same for the other
 * @param closed     whether it is over - struck and settled, or walked away from
 */
public record TradeViewPayload(
        String other,
        List<Pile> mine,
        List<Pile> theirs,
        boolean iAgreed,
        boolean theyAgreed,
        boolean closed,
        int revision) implements CustomPacketPayload {

    /** As many distinct cards as one side of a trade may hold. Matches the rule in :core. */
    public static final int MOST_PILES = dev.gathering.core.trade.TradeTable.MOST_DISTINCT;

    /** As long as a player name can be. */
    public static final int MOST_NAME_CHARACTERS = 64;

    /** So many of one card, on one side of the table. */
    public record Pile(CardComponent card, int count) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Pile> STREAM_CODEC =
                StreamCodec.composite(
                        CardComponent.STREAM_CODEC, Pile::card,
                        ByteBufCodecs.VAR_INT, Pile::count,
                        Pile::new);
    }

    public static final CustomPacketPayload.Type<TradeViewPayload> TYPE =
            GatheringPayloads.type("trade_view");

    /** One side of the table on the wire, bounded so a bad packet cannot allocate the world. */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Pile>> SIDE =
            Pile.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_PILES));

    /**
     * Written out by hand rather than composed.
     * <p>{@code StreamCodec.composite} stops at six parts in this version and a trade view has
     * seven, the seventh being the revision - which is the one that makes an agreement mean
     * the terms its sender read. The only thing to keep right is that the two halves stay in
     * step.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeViewPayload> STREAM_CODEC =
            StreamCodec.of(TradeViewPayload::toNetwork, TradeViewPayload::fromNetwork);

    private static void toNetwork(RegistryFriendlyByteBuf out, TradeViewPayload view) {
        ByteBufCodecs.stringUtf8(MOST_NAME_CHARACTERS).encode(out, view.other());
        SIDE.encode(out, view.mine());
        SIDE.encode(out, view.theirs());
        out.writeBoolean(view.iAgreed());
        out.writeBoolean(view.theyAgreed());
        out.writeBoolean(view.closed());
        ByteBufCodecs.VAR_INT.encode(out, view.revision());
    }

    private static TradeViewPayload fromNetwork(RegistryFriendlyByteBuf in) {
        String other = ByteBufCodecs.stringUtf8(MOST_NAME_CHARACTERS).decode(in);
        List<Pile> mine = SIDE.decode(in);
        List<Pile> theirs = SIDE.decode(in);
        boolean iAgreed = in.readBoolean();
        boolean theyAgreed = in.readBoolean();
        boolean closed = in.readBoolean();
        return new TradeViewPayload(
                other, mine, theirs, iAgreed, theyAgreed, closed, ByteBufCodecs.VAR_INT.decode(in));
    }

    public TradeViewPayload {
        other = other == null ? "" : other;
        mine = mine == null ? List.of() : List.copyOf(mine);
        theirs = theirs == null ? List.of() : List.copyOf(theirs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Nothing on the table, for the moment a trade ends. */
    public static TradeViewPayload over(String other) {
        return new TradeViewPayload(other, List.of(), List.of(), false, false, true, 0);
    }
}
