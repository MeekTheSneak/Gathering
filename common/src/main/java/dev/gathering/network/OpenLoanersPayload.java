package dev.gathering.network;

import dev.gathering.core.loaner.LoanerShelf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: here are the decks this server will lend you.
 * <p>Names only. What is in a loaner is a hundred cards, and a client that has not borrowed
 * one has no reason to be told - it gets the deck, with everything in it, at the moment it
 * takes one.
 *
 * @param table where the offer was made, so taking a deck can be answered at the same place
 */
public record OpenLoanersPayload(BlockPos table, List<String> names)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenLoanersPayload> TYPE =
            GatheringPayloads.type("open_loaners");

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLoanersPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenLoanersPayload::table,
                    ByteBufCodecs.stringUtf8(LoanerShelf.MOST_NAME_CHARACTERS)
                            .apply(ByteBufCodecs.list(LoanerShelf.MOST)),
                    OpenLoanersPayload::names,
                    OpenLoanersPayload::new);

    public OpenLoanersPayload {
        names = names == null ? List.of() : List.copyOf(names);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
