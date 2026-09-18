package dev.gathering.client;

import dev.gathering.core.ui.TablePose;
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
 * <p><b>Has a {@code clear}, so it must be named in {@link ClientState#forgetTheServer}</b> -
 * {@code tools/statecheck.py} fails the build otherwise, and it exists because three holders once
 * were not.
 * <p>Client-only. Written from the network thread's hand-off and read from the render thread, so
 * the map is concurrent and each entry is replaced rather than mutated.
 */
public final class ClientTablePointing {

    /**
     * How long a pointer stays believed without another one, in client ticks.
     * <p>A player whose client stopped talking - a lagging connection, a crash, a chunk unload -
     * should not leave an arm pointing at the felt forever. Long enough to ride out a hiccup,
     * short enough that a frozen arm is not something anybody has time to notice.
     */
    private static final int LASTS_TICKS = 40;

    /** One player's pointer: where it is now, where it was, and when it last changed. */
    private record Pointing(BlockPos table, float surfaceX, float surfaceY,
            float fromX, float fromY, long changedAtTick, boolean pointing) {
    }

    private static final Map<UUID, Pointing> POINTERS = new ConcurrentHashMap<>();

    private ClientTablePointing() {
    }

    /** Takes a pointer off the wire. */
    public static void accept(dev.gathering.network.TablePointingPayload payload) {
        throw new UnsupportedOperationException("""
                Not written yet. Replace this player's entry, keeping the previous position as the
                one to interpolate from and stamping the current client tick. A payload with
                pointing = false becomes an entry that will fade to rest rather than a removal, so
                the arm travels back instead of snapping.""");
    }

    /**
     * Where this player is pointing this frame, on the table they are pointing at.
     * <p>Empty for almost every player almost every frame, which is the answer this has to be
     * cheap about: it is asked once per drawn player per frame.
     *
     * @param partialTick how far into the current tick the frame is
     */
    public static Optional<Spot> pointedAt(UUID player, float partialTick) {
        throw new UnsupportedOperationException("""
                Not written yet. Empty when there is no entry, when the entry is older than
                LASTS_TICKS, or when its pointing flag is false and it has finished travelling back
                to rest. Otherwise the two positions with partialTick between them.""");
    }

    /** A place on a table's felt, in the surface's own units. */
    public record Spot(BlockPos table, double surfaceX, double surfaceY) {
    }

    /**
     * The pose this player should be drawn in, or resting.
     * <p>Here rather than in the mixins so both loaders get the same body, and so the one place
     * that turns a table coordinate into an arm angle is the one place that can be wrong about it.
     * The rotation from the table's frame into the player's own is {@link TableBodyPose}'s;
     * this is the lookup and the interpolation.
     */
    public static TablePose.Aim aimOf(UUID player, float partialTick) {
        throw new UnsupportedOperationException(
                "Not written yet. See TableBodyPose, which is what turns a Spot into an Aim.");
    }

    /** Drops everything this server told us. Named in {@link ClientState#forgetTheServer}. */
    public static void clear() {
        POINTERS.clear();
    }
}
