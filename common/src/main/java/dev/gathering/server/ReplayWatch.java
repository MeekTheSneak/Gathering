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
 * <p>Two questions and no third: what have you got, and show me step N of that one. The
 * client never receives the game itself - not the log, not the seed - only the picture of one
 * moment, folded here. That is what makes it safe to reuse the live board's screen for a
 * replay: the screen cannot tell the difference, and neither can a modified one.
 */
public final class ReplayWatch {

    /**
     * How many replays are held open at once.
     * <p>One per watcher, and there are never many: a replay is something one or two people
     * are looking at while everybody else plays. The oldest goes when the cap is reached,
     * which costs whoever it belonged to one slow frame and nothing else.
     */
    private static final int HELD = 8;

    /**
     * The replay each watcher has open, so scrubbing forward is one event rather than a fold
     * of the whole game. See {@link Replays.Watching}.
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
        WORKING.remove(who);
        LAST_FRAME.remove(who);
        WAITING.remove(who);
        WAITING_ON.remove(who);
        DRAINING.remove(who);
    }

    /** Between servers: one world's replays are not the next one's. */
    public static void clear() {
        OPEN.clear();
        WORKING.clear();
        Replays.stopWorking();
        LAST_FRAME.clear();
        WAITING.clear();
        WAITING_ON.clear();
        DRAINING.clear();
        Replays.clearHeaders();
    }

    /** Whether this server writes a finished game down at all. */
    public static boolean keeping() {
        return ServerSettings.get().modes().replays().keeps();
    }

    /**
     * Whether this player may watch this particular game back.
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

    /**
     * The shortest gap between two frames one watcher may ask for.
     * <p>Dragging a scrubber sends one of these per pixel, and every one folds a game to a
     * step on the server thread. Two ticks is faster than playback runs and slower than a
     * mouse can drag, so scrubbing costs a bounded amount of work rather than however much
     * somebody's hand can ask for.
     */
    private static final int TICKS_BETWEEN_FRAMES = 2;

    /** Per watcher, the server tick their last frame was answered on. */
    private static final java.util.Map<java.util.UUID, Integer> LAST_FRAME =
            new java.util.HashMap<>();

    /** Whether this watcher asked too recently to be answered again. */
    private static boolean tooFast(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_FRAME.get(player.getUUID());
        // A tick count that has gone backwards is a different server, so the wait is over.
        if (last != null && now >= last && now - last < TICKS_BETWEEN_FRAMES) {
            return true;
        }
        LAST_FRAME.put(player.getUUID(), now);
        return false;
    }

    /**
     * The newest frame each watcher has waiting on the throttle.
     * <p>Kept rather than dropped, and this is the second time that distinction has cost
     * something here. The throttle went in to bound a dragged scrubber, and it dropped what it
     * refused - which is right for a drag, where another request is a pixel away, and wrong
     * for the request that opens the replay in the first place. Picking a game within two
     * ticks of any other frame request meant the opening frame was thrown away, the client sat
     * on the list screen, and after five seconds it gave up quietly. A scripted client run
     * found it; nothing else could, because every other check here is about what the server
     * answers rather than about whether it answers at all.
     */
    private static final java.util.Map<java.util.UUID, int[]> WAITING = new java.util.HashMap<>();

    /** Which watchers already have a drain queued, so one is not queued per request. */
    private static final java.util.Set<java.util.UUID> DRAINING = new java.util.HashSet<>();

    /** Per watcher, which replay the waiting frame is of. */
    private static final java.util.Map<java.util.UUID, String> WAITING_ON =
            new java.util.HashMap<>();

    /**
     * Answers a waiting frame as soon as the throttle allows.
     * <p>Through the server's own task queue rather than a tick hook: the gap is two ticks
     * and a hook in both loaders would be a new seam for it. One drain per watcher at a time.
     */
    private static void drainWhenAllowed(ServerPlayer player) {
        java.util.UUID who = player.getUUID();
        if (!DRAINING.add(who)) {
            return;
        }
        player.server.execute(() -> {
            DRAINING.remove(who);
            int[] step = WAITING.get(who);
            String id = WAITING_ON.get(who);
            if (step == null || id == null || player.hasDisconnected()) {
                WAITING.remove(who);
                WAITING_ON.remove(who);
                return;
            }
            if (tooFast(player)) {
                drainWhenAllowed(player);
                return;
            }
            WAITING.remove(who);
            WAITING_ON.remove(who);
            answerFrame(player, id, step[0]);
        });
    }

    private static void sendFrame(ServerPlayer player, String id, int step) {
        if (tooFast(player)) {
            WAITING.put(player.getUUID(), new int[] {step});
            WAITING_ON.put(player.getUUID(), id);
            drainWhenAllowed(player);
            return;
        }
        answerFrame(player, id, step);
    }

    /**
     * Watchers whose replay is being read or folded on the worker right now.
     * <p>A held replay is a folded game that {@code frameAt} walks forward and rebuilds
     * backward, so two jobs on one watcher's replay would be two halves of two boards. While
     * a watcher is in here the server thread does not touch their {@code Watching} at all -
     * a frame asked for meanwhile waits on the throttle like any other.
     */
    private static final java.util.Set<java.util.UUID> WORKING = new java.util.HashSet<>();

    /**
     * How many records a step forward may apply before it is worth leaving the tick.
     * <p>Playback asks for the next step several times a second and each of those is a single
     * record applied to a board that is already there - a handful of microseconds, and moving
     * it to another thread would cost more in hops than it saved. A scrub of a hundred steps
     * is a different thing.
     */
    private static final int CHEAP_ENOUGH_TO_DO_HERE = 8;

    /** The frame itself, once the throttle has let it through. Server thread only. */
    private static void answerFrame(ServerPlayer player, String id, int step) {
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
        if (WORKING.contains(player.getUUID())) {
            // Their last frame is still being folded. Kept rather than dropped, and answered
            // when that finishes - the drain runs again on the tick after.
            WAITING.put(player.getUUID(), new int[] {step});
            WAITING_ON.put(player.getUUID(), id);
            drainWhenAllowed(player);
            return;
        }

        Replays.Watching open = OPEN.get(player.getUUID());
        boolean alreadyOpen = sameReplay(open, id);
        int wanted = alreadyOpen ? Math.clamp(step, 0, open.steps()) : Math.max(0, step);
        boolean cheap = alreadyOpen
                && wanted >= open.stepNow()
                && wanted - open.stepNow() <= CHEAP_ENOUGH_TO_DO_HERE;
        if (cheap) {
            sendTheFrame(player, id, open, wanted);
            return;
        }

        // Opening a replay reads a whole file; a scrub backwards folds the game again from
        // the front. Neither belongs in a tick, and both used to happen in one.
        WORKING.add(player.getUUID());
        long thisRun = ServerRun.generation();
        Replays.worker().execute(() -> {
            Folded folded;
            try {
                folded = foldOnTheWorker(player, id, step);
            } catch (RuntimeException couldNotFold) {
                folded = null;
            }
            Folded ready = folded;
            player.server.execute(() -> {
                WORKING.remove(player.getUUID());
                if (!ServerRun.isStill(thisRun) || player.hasDisconnected()) {
                    return;
                }
                if (ready == null) {
                    OPEN.remove(player.getUUID());
                    player.sendSystemMessage(
                            Component.translatable("message.gathering.replay_unreadable"));
                    return;
                }
                OPEN.put(player.getUUID(), ready.watching());
                Sending.to(player, new ReplayFramePayload(
                        id, ready.step(), ready.steps(), ready.view()));
            });
        });
    }

    /** What the worker produced: the replay it holds open now, and the frame it folded. */
    private record Folded(Replays.Watching watching, int step, int steps, byte[] view) {
    }

    /**
     * Opens or rewinds a replay and encodes one frame, on the worker.
     * <p>The watcher is in {@link #WORKING} for the whole of this, so nothing on the server
     * thread is looking at the replay it takes out of {@code OPEN} and puts back.
     */
    private static Folded foldOnTheWorker(ServerPlayer player, String id, int step) {
        Replays.Watching open = OPEN.get(player.getUUID());
        Replays.Watching watching = open != null && sameReplay(open, id)
                ? open
                : Replays.hold(id).orElse(null);
        if (watching == null) {
            return null;
        }
        int steps = watching.steps();
        int wanted = Math.clamp(step, 0, steps);
        try {
            return new Folded(watching, wanted, steps,
                    dev.gathering.core.game.persistence.ViewCodec.write(watching.frameAt(wanted)));
        } catch (IOException tooBigToSend) {
            return null;
        }
    }

    /** Sends a frame from a replay already folded to within a step or two of it. */
    private static void sendTheFrame(
            ServerPlayer player, String id, Replays.Watching watching, int wanted) {
        try {
            Sending.to(player, new ReplayFramePayload(id, wanted, watching.steps(),
                    dev.gathering.core.game.persistence.ViewCodec.write(watching.frameAt(wanted))));
        } catch (IOException tooBigToSend) {
            // A board that will not encode is a board nobody can be shown, and saying so is
            // better than a screen that opens onto nothing.
            player.sendSystemMessage(Component.translatable("message.gathering.replay_unreadable"));
        }
    }

    /** Whether the replay somebody has open is the one they are asking about. */
    private static boolean sameReplay(Replays.Watching open, String id) {
        return open != null && open.id().equals(id);
    }

    /**
     * The replay this player has open, opening it if this is a different one.
     * <p>One at a time per watcher: two open replays is two folded games held for somebody
     * who is looking at one of them.
     */

}
