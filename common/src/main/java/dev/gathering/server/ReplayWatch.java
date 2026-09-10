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
import net.minecraft.server.MinecraftServer;
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
        ServerTicks.forget("replay-frame:" + who);
    }

    /** Between servers: one world's replays are not the next one's. */
    public static void clear() {
        OPEN.clear();
        WORKING.clear();
        Replays.stopWorking();
        LAST_FRAME.clear();
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
     * One watcher's frame, throttled.
     * <p>A frame asked for inside the gap is kept and answered on a later tick rather than
     * dropped. Dropping it is what broke opening a replay at all: picking a game within two
     * ticks of any other frame request threw away the opening frame, and the client sat on
     * the list screen and quietly gave up.
     * <p>Through {@link ServerTicks} rather than {@code server.execute}, which runs a task
     * inline when it is called on the server thread - the first shape of this waited by
     * re-entering itself, which cannot terminate.
     */
    private static void sendFrame(ServerPlayer player, String id, int step) {
        if (tooFast(player)) {
            ServerTicks.on(waitingKey(player), lastFrameTick(player) + TICKS_BETWEEN_FRAMES,
                    () -> answerFrame(player, id, step));
            return;
        }
        answerFrame(player, id, step);
    }

    /** What a watcher's pending frame is filed under, so a newer one replaces it. */
    private static Object waitingKey(ServerPlayer player) {
        return "replay-frame:" + player.getUUID();
    }

    /** Which tick this watcher was last answered on, or a long time ago. */
    private static int lastFrameTick(ServerPlayer player) {
        return LAST_FRAME.getOrDefault(player.getUUID(), Integer.MIN_VALUE / 2);
    }

    /**
     * Which job each watcher has out on the worker, by a number no two jobs share.
     * <p>A held replay is a folded game that {@code frameAt} walks forward and rebuilds
     * backward, so two jobs on one watcher's replay would be two halves of two boards. While
     * a watcher is in here the server thread does not touch their {@code Watching} at all -
     * a frame asked for meanwhile waits a tick.
     * <p>A number rather than a flag, because a flag is not owned by anybody. A watcher who
     * disconnects and returns while a fold is out gets a new job; the old completion, landing
     * afterwards, would have cleared the new job's guard and let a second fold start on the
     * replay the first one is still holding. Now it finds a number that is not its own and
     * does nothing at all.
     */
    private static final java.util.Map<java.util.UUID, Long> WORKING = new java.util.HashMap<>();

    /** A number no two folds share, for the life of this process. */
    private static final java.util.concurrent.atomic.AtomicLong JOBS =
            new java.util.concurrent.atomic.AtomicLong();

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
        if (WORKING.containsKey(player.getUUID())) {
            // Their last frame is still being folded. Kept rather than dropped, and asked
            // again a tick later, by which time the fold has usually landed.
            ServerTicks.on(waitingKey(player), player.server.getTickCount() + 1,
                    () -> answerFrame(player, id, step));
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
        //
        // The replay is taken out of OPEN here, on the server thread, and handed to the
        // worker as a plain reference - the worker never touches the map. OPEN is an
        // access-ordered LinkedHashMap, so even a get from another thread rewrites its links
        // while the server thread is inserting and evicting in it.
        java.util.UUID who = player.getUUID();
        Replays.Watching held = alreadyOpen ? OPEN.remove(who) : null;
        long job = JOBS.incrementAndGet();
        WORKING.put(who, job);
        long thisRun = ServerRun.generation();
        MinecraftServer asking = player.server;
        Replays.worker().execute(() -> {
            Folded folded;
            try {
                folded = fold(held, id, step);
            } catch (RuntimeException couldNotFold) {
                folded = null;
            }
            Folded ready = folded;
            ServerRun.onTheServerThread(asking, thisRun, () -> {
                if (!Long.valueOf(job).equals(WORKING.get(who))) {
                    // Somebody else's job owns this watcher now - they left and came back
                    // while this was folding. Nothing here is theirs to put away.
                    return;
                }
                WORKING.remove(who);
                if (player.hasDisconnected()) {
                    return;
                }
                if (ready == null) {
                    player.sendSystemMessage(
                            Component.translatable("message.gathering.replay_unreadable"));
                    return;
                }
                OPEN.put(who, ready.watching());
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
     * <p>Everything it needs was handed to it. It reads no shared state and writes none: the
     * replay it works on was taken out of the server's map before this was submitted, and the
     * result goes back through the server thread. The first draft called {@code OPEN.get}
     * from here, which is a read of an access-ordered map that another thread is writing.
     *
     * @param held the replay this watcher already had open, or null to read it fresh
     */
    private static Folded fold(Replays.Watching held, String id, int step) {
        Replays.Watching watching = held != null ? held : Replays.hold(id).orElse(null);
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
