package dev.gathering.server;

import dev.gathering.core.game.PlayerText;
import dev.gathering.network.Sending;
import dev.gathering.network.TableChatPayload;
import dev.gathering.network.TableSaidPayload;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Talking to the table rather than to the server.
 *
 * <p>Half of a game of Magic is conversation - "attacking you with everything", "hold on, in
 * response", "what are you on" - and on a shared server the global chat is the wrong room for
 * it. Everybody mining two hundred blocks away reads a turn they cannot see, and the four
 * people who need it lose it under the noise. So a table is its own room.
 *
 * <p><b>Who hears it.</b> Everybody the board goes to: the players sitting at it and anybody
 * near enough to be watching the miniature on the table top. That is deliberately the same set
 * rather than a second rule - a person who can see your game is a person at your game, and a
 * table where the watchers can see the cards but not hear the players is a table with a pane
 * of glass across it.
 *
 * <p><b>Who may speak.</b> Anybody standing at the table, seated or not, by the one reach rule
 * every other verb uses. Speaking needs you close enough to be at the table; hearing only needs
 * you close enough to watch it, which is further - exactly as it is in a room.
 *
 * <p>Nothing here is a game event. Chat is not a move: it is not folded, it is not in the
 * session log, and undo cannot reach it. A rewind that struck out what somebody said would be
 * a mod editing a conversation.
 *
 * <p>Server thread only.
 */
public final class TableTalk {

    /**
     * The shortest gap between two things one player may say.
     *
     * <p>Not a punishment - it is a fifth of a second, and nobody types that fast. It is there
     * because this is a client-triggered broadcast to every player near a table, which is
     * exactly the shape of thing that is worth flooding, and the cost of the guard is nothing.
     */
    private static final long QUIET_MILLIS = 200L;

    /** When each player last said something. Server thread only, so a plain map is right. */
    private static final Map<UUID, Long> LAST_SPOKE = new HashMap<>();

    private TableTalk() {
    }

    public static void handle(ServerPlayer player, TableChatPayload payload) {
        BlockPos origin = TableReach.originFor(player, payload.table()).orElse(null);
        if (origin == null) {
            return;
        }
        String said = PlayerText.oneLine(payload.text(), TableChatPayload.LONGEST);
        if (said == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long before = LAST_SPOKE.get(player.getUUID());
        if (before != null && now - before < QUIET_MILLIS) {
            return;
        }
        LAST_SPOKE.put(player.getUUID(), now);

        TableSaidPayload line = new TableSaidPayload(
                origin, player.getGameProfile().getName(), said);
        for (ServerPlayer listener : everybodyAt(player.serverLevel(), origin)) {
            Sending.to(listener, line);
        }
    }

    /**
     * Everyone at this table: the players sitting at it and the people watching it.
     *
     * <p>Built as a set because the two lists overlap the moment a seated player is also
     * within earshot of their own table, which is always.
     */
    private static Set<ServerPlayer> everybodyAt(ServerLevel level, BlockPos origin) {
        Set<ServerPlayer> heard = new LinkedHashSet<>();
        for (TableBroadcast.Seated seated : TableBroadcast.seatedAt(level, origin)) {
            heard.add(seated.player());
        }
        heard.addAll(TableBroadcast.watchingNearby(level, origin));
        return heard;
    }

    /** Forgets a player who has left, so the map does not grow for the life of the server. */
    public static void forget(UUID player) {
        LAST_SPOKE.remove(player);
    }
}
