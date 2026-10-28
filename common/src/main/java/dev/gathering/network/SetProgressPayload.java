package dev.gathering.network;

import dev.gathering.core.collection.SetCompletion;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: how much of each set is in this collection.
 * <p>One row per set, already worked out. A few dozen numbers rather than the ten thousand
 * cards they were worked out from, which is the shape every other collection payload has for
 * the same reason.
 *
 * @param stillLooking how many distinct cards in the collection the server cannot yet name.
 *     Sent rather than swallowed: a total quietly short by two hundred cards is a screen
 *     lying about the one number somebody opened it for, and the honest answer is to say the
 *     count is still settling and let it settle
 */
public record SetProgressPayload(BlockPos collection, List<Row> sets, int stillLooking)
        implements CustomPacketPayload {

    /**
     * How many sets are worth sending.
     * <p>A collection can touch every set there has ever been, and a list of nine hundred
     * rows is not a thing anybody reads. The ones nearest finishing come first, so what is
     * cut is the tail nobody scrolls to.
     */
    public static final int MOST_SETS = 128;

    /** A set code is short, and a set name is a name. */
    public static final int LONGEST_CODE = 8;
    public static final int LONGEST_NAME = 96;

    /** One set: what it is, how much of it is here, and how many extras came with it. */
    public record Row(String code, String name, int owned, int size, int extras) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(LONGEST_CODE), Row::code,
                        ByteBufCodecs.stringUtf8(LONGEST_NAME), Row::name,
                        ByteBufCodecs.VAR_INT, Row::owned,
                        ByteBufCodecs.VAR_INT, Row::size,
                        ByteBufCodecs.VAR_INT, Row::extras,
                        Row::new);

        public static Row of(SetCompletion progress) {
            return new Row(progress.code(), progress.name(), progress.owned(),
                    progress.size(), progress.extras());
        }

        /** Back into the shape that knows the arithmetic, so the client does none of it. */
        public SetCompletion asProgress() {
            return new SetCompletion(code, name, owned, size, extras);
        }
    }

    public static final CustomPacketPayload.Type<SetProgressPayload> TYPE =
            GatheringPayloads.type("set_progress");

    public static final StreamCodec<RegistryFriendlyByteBuf, SetProgressPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetProgressPayload::collection,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_SETS)), SetProgressPayload::sets,
                    ByteBufCodecs.VAR_INT, SetProgressPayload::stillLooking,
                    SetProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
