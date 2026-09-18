package dev.gathering.server;

import dev.gathering.core.ui.TableTop;
import dev.gathering.network.Sending;
import dev.gathering.network.TablePointPayload;
import dev.gathering.network.TablePointingPayload;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passing on where a seated player is pointing, and refusing to pass on anything else.
 * <p>The thinnest server-side thing in the mod, on purpose: it holds no state that matters, it
 * writes nothing down, and a pointer that never arrives costs a player an arm animation rather
 * than a card. What it does do is check, because the alternative is a payload that lets any
 * client make any other client draw anything anywhere.
 * <p>Three refusals, all silent. The player is not seated at the table they named; the point is
 * not on that table's felt; or they are sending faster than a body moves. Silent because none of
 * them is something a player did - they are all something a client did - and an action bar line
 * about a packet is noise in the middle of a game.
 * <p>Server thread only.
 */
public final class TablePointing {

    /**
     * How many pointers one player may send before the rest are dropped, and how fast that
     * refills.
     * <p>Generous against what the sender actually does - four or five a second - and mean
     * against a client that has decided to send one per mouse event to every player in the room.
     * An {@link ActionBudget} rather than a counter of its own, because the mod already has one
     * answer to "this player is asking too often" and two would be two to keep in step.
     */
    public static final ActionBudget POINTERS = new ActionBudget(10, 20);

    /**
     * Who is currently pointing, so a stop can be sent only for somebody who was.
     * <p>Weakly keyed on the player so a disconnect that never reaches {@link #stopped} does not
     * keep an entry for ever. What it holds is the table, because a stop has to be sent to the
     * people watching <em>that</em> table - by the time a player has stood up, the server can no
     * longer ask which one they were at.
     */
    private static final Map<ServerPlayer, BlockPos> POINTING_AT = new WeakHashMap<>();

    /**
     * Told of every pointer as it goes out: who it is for, and what they were told. Empty unless
     * a test adds to it.
     * <p>The same seam {@code TableBroadcast.builtForTesting} is, and for the same reason: a
     * game test's stand-in player cannot take a payload at all - {@code Sending.to} refuses it,
     * deliberately - so a test cannot read the wire and reads this instead, one step before it.
     * What it is for is the refusals: "a player who is not seated here cannot move somebody
     * else's arm" is not a claim worth making without a test that fails when the check goes.
     * <p><b>A list rather than one slot.</b> Game tests in a batch run alongside each other, so a
     * single field is a collector the next test to start quietly replaces - and a test whose
     * collector has been taken away passes by observing nothing. Each watcher filters on the
     * player it cares about, which is also what keeps one test's pointers out of another's.
     */
    private static final java.util.List<java.util.function.BiConsumer<UUID, TablePointingPayload>>
            WATCHERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Watches every pointer that goes out. For tests; see {@link #WATCHERS}. */
    public static void watchForTesting(
            java.util.function.BiConsumer<UUID, TablePointingPayload> watcher) {
        WATCHERS.add(watcher);
    }

    /** Stops every test watcher. */
    public static void forgetTestWatchers() {
        WATCHERS.clear();
    }

    private TablePointing() {
    }

    /**
     * Takes a pointer from a client and tells the table about it.
     * <p>The shape {@code GatheringProtocol} hands a handler: the player the loader has already
     * confirmed, then the payload.
     */
    public static void handle(ServerPlayer player, TablePointPayload payload) {
        if (player == null || payload == null || payload.table() == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos table = payload.table();
        if (!payload.pointing()) {
            // A stop is not rate-limited and needs no seat: a player who has just stood up is no
            // longer seated, and refusing their stop is how an arm gets left pointing for ever.
            forget(player, table);
            return;
        }
        // Seated at the table they named, or this is a client talking about somebody else's game.
        if (TableBroadcast.seatOf(level, table, player.getUUID()).isEmpty()) {
            return;
        }
        if (!onTheFelt(level, table, payload.surfaceX(), payload.surfaceY())) {
            return;
        }
        if (!POINTERS.spend(player.getUUID(), 1)) {
            return;
        }
        POINTING_AT.put(player, table);
        tellTheRoom(level, table, new TablePointingPayload(
                player.getUUID(), table, payload.surfaceX(), payload.surfaceY(), true), player);
    }

    /**
     * Says a player has stopped pointing, for the paths that are not a payload: standing up,
     * leaving the seat, logging out, the session ending.
     * <p>Here rather than left to the client, because the client that stops pointing is the one
     * that disconnected and the clients that need to know are the other ones. An arm left pointing
     * at a table by somebody who went home is the kind of thing nobody reports and everybody sees.
     */
    public static void stopped(ServerPlayer player) {
        if (player == null) {
            return;
        }
        forget(player, POINTING_AT.get(player));
    }

    /** Forgets this player's pointer and tells whoever was watching that table. */
    private static void forget(ServerPlayer player, BlockPos table) {
        if (POINTING_AT.remove(player) == null && table == null) {
            // Was not pointing and is not now. Saying so would be a packet to the whole room for
            // every tick of every player who has never opened a table.
            return;
        }
        if (table == null) {
            return;
        }
        tellTheRoom(player.serverLevel(), table, new TablePointingPayload(
                player.getUUID(), table, 0, 0, false), player);
    }

    /**
     * Everybody who can see this table, except the player whose arm it is.
     * <p>Their own body is not drawn for them in the table view and is behind the camera outside
     * it, so the packet would be one more per player per pointer for nothing - which at a full
     * pod is the difference between four packets and five.
     */
    private static void tellTheRoom(ServerLevel level, BlockPos table,
            TablePointingPayload said, ServerPlayer except) {
        for (ServerPlayer watching : TableBroadcast.watchingNearby(level, table)) {
            if (!watching.getUUID().equals(except.getUUID())) {
                for (var watcher : WATCHERS) {
                    watcher.accept(watching.getUUID(), said);
                }
                Sending.to(watching, said);
            }
        }
    }

    /**
     * Whether a point is on this cluster's own felt.
     * <p>Built the way the camera builds it - from the board's seat count and whether the cluster
     * lies turned - so a client cannot name a point on a table three times the size of the one
     * that is there. A pod is four tables and a surface four times as wide, and the difference is
     * exactly what this check is for.
     */
    private static boolean onTheFelt(ServerLevel level, BlockPos table, double x, double y) {
        int seats = dev.gathering.block.TableSessions.sessionAt(level, table)
                .map(session -> session.state().seats().size())
                .orElse(dev.gathering.core.table.TableCluster.SEATS_PER_TABLE);
        int tables = Math.max(1, (seats + dev.gathering.core.table.TableCluster.SEATS_PER_TABLE - 1)
                / dev.gathering.core.table.TableCluster.SEATS_PER_TABLE);
        boolean turned = dev.gathering.block.TableClusters.at(level, table).turned();
        TableTop top = TableTop.forCluster(
                table.getX(), table.getY(), table.getZ(), tables, 1, turned);
        return x >= 0 && y >= 0 && x <= top.surfaceWidth() && y <= top.surfaceDepth();
    }

    /** Forgets every pointer, for a server that is stopping. */
    public static void clear() {
        POINTING_AT.clear();
        POINTERS.clear();
    }
}
