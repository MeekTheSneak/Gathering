package dev.gathering.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: more than one token answers to that name - which did you mean?
 * <p>Sent instead of making one, when a typed name matches tokens that differ. Each choice is
 * the summary a client would be sent for that card anyway, so the chooser can say what tells
 * them apart - size, colors, what it does - and the pick comes back as a {@link MakeTokenPayload}
 * for that printing, at the count already asked for.
 *
 * @param asked   the name the player typed, to title the question with
 * @param count   how many they asked for, carried back with the pick
 * @param choices the distinct tokens of that name, newest first, at most {@link #MOST_CHOICES}
 */
public record TokenChoicesPayload(BlockPos table, String asked, int count, List<CardSummary> choices)
        implements AtATable {

    /** More distinct tokens than any one name has, and few enough to be a list somebody reads. */
    public static final int MOST_CHOICES = 24;

    public static final CustomPacketPayload.Type<TokenChoicesPayload> TYPE =
            GatheringPayloads.type("token_choices");

    public static final StreamCodec<RegistryFriendlyByteBuf, TokenChoicesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TokenChoicesPayload::table,
                    ByteBufCodecs.stringUtf8(CreateTokenPayload.MAX_NAME), TokenChoicesPayload::asked,
                    ByteBufCodecs.VAR_INT, TokenChoicesPayload::count,
                    CardSummary.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_CHOICES)),
                    TokenChoicesPayload::choices,
                    TokenChoicesPayload::new);

    public TokenChoicesPayload {
        asked = asked == null ? "" : asked;
        count = Math.max(1, Math.min(CreateTokenPayload.MAX_COUNT, count));
        choices = List.copyOf(choices.subList(0, Math.min(choices.size(), MOST_CHOICES)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
