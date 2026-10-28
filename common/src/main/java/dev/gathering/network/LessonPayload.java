package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the guided first game began, or was finished, with these steps done.
 * <p>The lesson is played on a board this client builds for itself, so this is the only thing the server ever
 * hears of it. The server holds a finish to the lesson having begun, long enough ago to have been played, with
 * every step done - see {@link dev.gathering.server.LessonRecords}.
 *
 * @param finished false for "it began", true for "it was finished"
 * @param steps    the steps done, by name, when finished
 */
public record LessonPayload(boolean finished, List<String> steps) implements CustomPacketPayload {

    /** More steps than the lesson has. */
    public static final int MOST_STEPS = 16;

    public static final CustomPacketPayload.Type<LessonPayload> TYPE = GatheringPayloads.type("lesson");

    public static final StreamCodec<RegistryFriendlyByteBuf, LessonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LessonPayload::finished,
            ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(MOST_STEPS)), LessonPayload::steps,
            LessonPayload::new);

    public LessonPayload {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
