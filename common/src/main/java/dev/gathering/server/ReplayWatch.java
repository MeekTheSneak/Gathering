package dev.gathering.server;

import dev.gathering.network.ReplayFramePayload;
import dev.gathering.network.ReplayListPayload;
import dev.gathering.network.Sending;
import dev.gathering.network.WatchReplayPayload;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Answering a client that wants to watch a game back.
 *
 * <p>Two questions and no third: what have you got, and show me step N of that one. The
 * client never receives the game itself - not the log, not the seed - only the picture of one
 * moment, folded here. That is what makes it safe to reuse the live board's screen for a
 * replay: the screen cannot tell the difference, and neither can a modified one.
 */
public final class ReplayWatch {

    /**
     * How many replays are held open at once.
     *
     * <p>One per watcher, and there are never many: a replay is something one or two people
     * are looking at while everybody else plays. The oldest goes when the cap is reached,
     * which costs whoever it belonged to one slow frame and nothing else.
     */
    private static final int HELD = 8;

    /**
     * The replay each watcher has open, so scrubbing forward is one event rather than a fold
     * of the whole game. See {@link Replays.Watching}.
     *
     * <p>Server thread only, which is where every payload handler in this mod runs. Access
     * ordered, so the entry that goes is the one nobody has looked at for longest rather than
     * the one that happens to be first in a hash.
     */
    private static final java.util.LinkedHashMap<java.util.UUID, Replays.Watching> OPEN =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<java.util.UUID, Replays.Watching> eldest) {
                    return size() > HELD;
                }
            };

    private ReplayWatch() {
    }

    /** Between servers, and when somebody logs out with a replay open. */
    public static void forget(java.util.UUID who) {
        OPEN.remove(who);
    }

    /** Between servers: one world's replays are not the next one's. */
    public static void clear() {
        OPEN.clear();
    }

    /** Whether this server writes a finished game down at all. */
    public static boolean keeping() {
        return ServerSettings.get().modes().replays().keeps();
    }

    /**
     * Whether this player may watch this particular game back.
     *
     * <p>The middle setting is the one most groups want: settle your own argument about what
     * was on top of the library without a stranger reading your deck for the rematch. An
     * operator may open any of them, because an operator can read the file anyway and the
     * question they are usually answering is somebody else's complaint.
     */
    public static boolean mayWatch(ServerPlayer player, Replays.Record kept) {
        return switch (ServerSettings.get().modes().replays()) {
            case PUBLIC -> true;
            case PARTICIPANTS -> kept.wasPlayedBy(player.getUUID())
                    || player.hasPermissions(2);
            case OFF -> false;
        };
    }

    /** The list, or one frame. An empty id is the list. */
    public static void handle(ServerPlayer player, WatchReplayPayload asked) {
        if (player == null || asked == null) {
            return;
        }
        if (!keeping()) {
            player.sendSystemMessage(Component.translatable("message.gathering.replays_off"));
            return;
        }
        if (asked.id().isBlank()) {
            sendList(player);
            return;
        }
        sendFrame(player, asked.id(), asked.step());
    }

    /** What is on the shelf. Also the command's answer, which is why it is public. */
    public static void sendList(ServerPlayer player) {
        List<ReplayListPayload.Game> rows = new ArrayList<>();
        for (Replays.Record kept : Replays.kept()) {
            if (!mayWatch(player, kept)) {
                continue;
            }
            rows.add(new ReplayListPayload.Game(
                    kept.id(),
                    kept.when(),
                    kept.players().isEmpty()
                            ? Component.translatable("screen.gathering.replay.nobody").getString()
                            : String.join(", ", kept.names()),
                    kept.turns(),
                    kept.steps()));
            if (rows.size() >= ReplayListPayload.MAX_GAMES) {
                break;
            }
        }
        Sending.to(player, new ReplayListPayload(List.copyOf(rows)));
    }

    private static void sendFrame(ServerPlayer player, String id, int step) {
        // Checked here as well as when the list went out. The list is a courtesy; this is the
        // fence, because an id is a string on the wire and nothing stops a client sending one
        // it was never shown.
        //
        // One header read rather than the whole shelf. A scrubbed replay asks for a frame
        // several times a second, and listing every kept game to find one of them read and
        // parsed sixty-four headers per step.
        Replays.Record kept = Replays.headerOf(id).orElse(null);
        if (kept == null || !mayWatch(player, kept)) {
            player.sendSystemMessage(Component.translatable("message.gathering.replay_unreadable"));
            return;
        }
        Replays.Watching watching = heldFor(player, id);
        if (watching == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.replay_unreadable"));
            return;
        }
        int steps = watching.steps();
        int wanted = Math.clamp(step, 0, steps);
        try {
            Sending.to(player, new ReplayFramePayload(id, wanted, steps,
                    dev.gathering.core.game.persistence.ViewCodec.write(watching.frameAt(wanted))));
        } catch (IOException tooBigToSend) {
            // A board that will not encode is a board nobody can be shown, and saying so is
            // better than a screen that opens onto nothing.
            player.sendSystemMessage(Component.translatable("message.gathering.replay_unreadable"));
        }
    }

    /**
     * The replay this player has open, opening it if this is a different one.
     *
     * <p>One at a time per watcher: two open replays is two folded games held for somebody
     * who is looking at one of them.
     */
    private static Replays.Watching heldFor(ServerPlayer player, String id) {
        Replays.Watching open = OPEN.get(player.getUUID());
        if (open != null && open.id().equals(id)) {
            return open;
        }
        Replays.Watching fresh = Replays.hold(id).orElse(null);
        if (fresh != null) {
            OPEN.put(player.getUUID(), fresh);
        } else {
            OPEN.remove(player.getUUID());
        }
        return fresh;
    }
}
