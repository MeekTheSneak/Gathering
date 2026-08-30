package dev.gathering.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the finished games this server still has, newest first.
 *
 * <p>Headers only. Nothing in here is a board - the players' names, when it was, and how long
 * it ran - so the list costs one small packet however many games are on the shelf. A frame
 * arrives only when somebody actually asks to watch one.
 */
public record ReplayListPayload(List<Game> games) implements CustomPacketPayload {

    /** {@code Replays.KEPT} is the real bound; this is the one the wire enforces. */
    public static final int MAX_GAMES = 128;

    /**
     * One row of the list.
     *
     * @param players already joined into one line, because the list is the only thing that
     *                reads it and a list of lists on the wire buys nothing
     */
    public record Game(String id, long when, String players, int turns, int steps) {
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Game> GAME =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(WatchReplayPayload.MAX_ID), Game::id,
                    ByteBufCodecs.VAR_LONG, Game::when,
                    ByteBufCodecs.stringUtf8(256), Game::players,
                    ByteBufCodecs.VAR_INT, Game::turns,
                    ByteBufCodecs.VAR_INT, Game::steps,
                    Game::new);

    public static final CustomPacketPayload.Type<ReplayListPayload> TYPE =
            GatheringPayloads.type("replay_list");

    public static final StreamCodec<RegistryFriendlyByteBuf, ReplayListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    GAME.apply(ByteBufCodecs.list(MAX_GAMES)), ReplayListPayload::games,
                    ReplayListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
