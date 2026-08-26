package dev.gathering.network;

import dev.gathering.core.game.RandomPick;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: take this many cards out of my hand, and do not let me choose which.
 *
 * <p>The one verb at this table that the client is deliberately not trusted with. Everything
 * else here is a move a player makes and the server writes down, because that is what a table
 * with no rules engine is - but the whole value of a discard at random is that the person
 * doing it did not choose, and a client that picked its own two cards would look exactly like
 * one that picked its worst two. Nobody could tell, including the person doing it.
 *
 * <p>So it is a request for an outcome rather than an instruction to move named cards. There
 * are no card ids in it to name.
 *
 * <p>Carries no seat: it is your own hand, and which hand that is comes from the player the
 * packet arrived from. A client asking to discard out of somebody else's hand is asking to
 * reach across the table, and there is nothing here to ask with.
 */
public record DiscardAtRandomPayload(BlockPos table, int howMany) implements CustomPacketPayload {

    public DiscardAtRandomPayload {
        howMany = Math.max(1, Math.min(RandomPick.MOST_AT_ONCE, howMany));
    }

    public static final CustomPacketPayload.Type<DiscardAtRandomPayload> TYPE =
            GatheringPayloads.type("discard_at_random");

    public static final StreamCodec<RegistryFriendlyByteBuf, DiscardAtRandomPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DiscardAtRandomPayload::table,
                    ByteBufCodecs.VAR_INT, DiscardAtRandomPayload::howMany,
                    DiscardAtRandomPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
