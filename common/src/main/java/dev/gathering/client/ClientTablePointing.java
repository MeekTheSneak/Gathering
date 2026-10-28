package dev.gathering.client;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/**
 * Where each player this client can see is pointing, and where they were pointing before that.
 * <p>Two of each because pointers arrive four or five times a second and frames are drawn sixty.
 * A frame sits between the last two, which is what turns a sequence of positions into an arm
 * moving. Anything cleverer - a spring, a spline - would be invisible at this arc and would have
 * to be right about a body rather than about a mouse.
 * <p>Nothing here is authoritative about anything. It is a hint about how to draw somebody, it is
 * thrown away when the server goes, and a client that never receives a pointer draws every arm at
 * rest, which is what the mod looked like before this existed.
 * <p><b>Has a {@code clear}, so it is named in {@link ClientState#forgetTheServer}</b> -
 * {@code tools/statecheck.py} fails the build otherwise, and it exists because three holders once
 * were not.
 * <p>Client-only. Written from the network thread's hand-off and read from the render thread, so
 * the map is concurrent and each entry is replaced rather than mutated.
 */
public final class ClientTablePointing {

    /**
     * How long a pointer stays believed without another one, in client ticks.
     * <p>A player whose client stopped talking - a lagging connection, a crash, a chunk unload -
     * should not leave an arm pointing at the felt for ever. Long enough to ride out a hiccup,
     * short enough that a frozen arm is not something anybody has time to notice.
     */
    private static final int LASTS_TICKS = 40;

    /**
     * How long an arm takes to travel back to rest once its owner has stopped, in ticks.
     * <p>A stop that dropped the entry would snap the arm down between two frames. Traveling
     * back is the same interpolation the other way, and it is short because a hand coming off a
     * table is quick.
     */
    private static final int SETTLES_TICKS = 6;

    /**
     * How long the hand takes to travel from the last point to the new one, in ticks.
     * <p>The sender's own cadence, so the hand arrives at each point about as the next one does.
     * Shorter and the arm is still, then jumps; longer and it is always behind the cursor.
     */
    private static final float TRAVEL_TICKS = 4;

    /** One player's pointer: where it is now, where it was, and when it last changed. */
    private record Pointing(BlockPos table, float surfaceX, float surfaceY,
            float fromX, float fromY, long changedAtTick, boolean pointing) {
    }

    private static final Map<UUID, Pointing> POINTERS = new ConcurrentHashMap<>();

    /**
     * The client's own tick count, which is what "when" means here.
     * <p>Its own counter rather than the level's game time: a client watching a paused single
     * player world still draws frames, and an arm that froze because the world did would be a
     * body that has stopped being alive.
     */
    private static volatile long tick;

    private ClientTablePointing() {
    }

    /** Called once per client tick, before anything reads a pointer. */
    public static void tick() {
        tick++;
    }

    /** Takes a pointer off the wire. */
    public static void accept(dev.gathering.network.TablePointingPayload payload) {
        if (payload == null || payload.player() == null) {
            return;
        }
        Pointing had = POINTERS.get(payload.player());
        // Where the arm is right now becomes where it travels from, so a pointer arriving mid
        // journey does not jump the hand back to the last one the server sent.
        float fromX = had == null ? payload.surfaceX() : had.surfaceX();
        float fromY = had == null ? payload.surfaceY() : had.surfaceY();
        if (!payload.pointing()) {
            // Not a removal: an entry that will fade to rest, so the arm travels back down.
            if (had == null) {
                return;
            }
            POINTERS.put(payload.player(), new Pointing(
                    had.table(), had.surfaceX(), had.surfaceY(), fromX, fromY, tick, false));
            return;
        }
        PoseProbe.sawPointer(payload);
        POINTERS.put(payload.player(), new Pointing(payload.table(),
                payload.surfaceX(), payload.surfaceY(), fromX, fromY, tick, true));
    }

    /**
     * Where this player is pointing this frame, on the table they are pointing at.
     * <p>Empty for almost every player almost every frame, which is the answer this has to be
     * cheap about: it is asked once per drawn player per frame.
     *
     * @param partialTick how far into the current tick the frame is
     */
    public static Optional<Spot> pointedAt(UUID player, float partialTick) {
        Pointing pointing = player == null ? null : POINTERS.get(player);
        if (pointing == null) {
            return Optional.empty();
        }
        long since = tick - pointing.changedAtTick();
        if (since > LASTS_TICKS) {
            // Stale. Dropped here rather than swept, because the only thing that asks is the
            // thing that draws, and a map of a few players is not worth a sweep.
            POINTERS.remove(player, pointing);
            return Optional.empty();
        }
        if (!pointing.pointing() && since >= SETTLES_TICKS) {
            POINTERS.remove(player, pointing);
            return Optional.empty();
        }
        float age = since + partialTick;
        // Two clocks, because they are two different journeys: the hand slides to the new point
        // at the rate pointers arrive, and the whole arm falls to rest at the rate a hand comes
        // off a table. Running both off one number made an arm that crawled back down.
        float moved = Math.max(0f, Math.min(1f, age / TRAVEL_TICKS));
        float settling = pointing.pointing()
                ? 1f
                : Math.max(0f, 1f - age / SETTLES_TICKS);
        return Optional.of(new Spot(pointing.table(),
                pointing.fromX() + (pointing.surfaceX() - pointing.fromX()) * moved,
                pointing.fromY() + (pointing.surfaceY() - pointing.fromY()) * moved,
                settling));
    }

    /**
     * A place on a table's felt, in the surface's own units, and how much of the pose to apply.
     *
     * @param settling one while the arm is out, falling to zero as it travels back to rest, so a
     *     player who has stopped pointing lowers their arm rather than having it vanish
     */
    public record Spot(BlockPos table, double surfaceX, double surfaceY, float settling) {
    }

    /** Drops everything this server told us. Named in {@link ClientState#forgetTheServer}. */
    public static void clear() {
        POINTERS.clear();
        tick = 0;
    }
}
