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

    private ReplayWatch() {
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
        Replays.Record kept = Replays.kept().stream()
                .filter(record -> record.id().equals(id))
                .findFirst()
                .orElse(null);
        if (kept == null || !mayWatch(player, kept)) {
            player.sendSystemMessage(Component.translatable("message.gathering.replay_unreadable"));
            return;
        }
        int steps = Replays.stepsIn(id);
        int wanted = Math.clamp(step, 0, steps);
        Replays.frameOf(id, wanted).ifPresentOrElse(frame -> {
            try {
                Sending.to(player, new ReplayFramePayload(
                        id, wanted, steps, dev.gathering.core.game.persistence.ViewCodec.write(frame)));
            } catch (IOException tooBigToSend) {
                // A board that will not encode is a board nobody can be shown, and saying so
                // is better than a screen that opens onto nothing.
                player.sendSystemMessage(Component.translatable("message.gathering.replay_unreadable"));
            }
        }, () -> player.sendSystemMessage(
                Component.translatable("message.gathering.replay_unreadable")));
    }
}
