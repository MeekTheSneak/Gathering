package dev.gathering.server;

import dev.gathering.network.TablePointPayload;
import dev.gathering.network.TablePointingPayload;
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
     * How many pointers one player may send a second before the rest are dropped.
     * <p>Generous against what the sender actually does - four or five - and mean against a
     * client that has decided to send one per mouse event to every player in the room.
     */
    private static final int A_SECOND = 10;

    private TablePointing() {
    }

    /**
     * Takes a pointer from a client and tells the table about it.
     * <p>The signature {@code GatheringProtocol} wants: the payload and the player who sent it,
     * on the server thread, having already been handed over by whichever loader took it off
     * the wire.
     */
    public static void handle(TablePointPayload payload, ServerPlayer player) {
        throw new UnsupportedOperationException("""
                Not written yet. What it has to do, in order:
                  1. TableBroadcast.seatOf(player.serverLevel(), payload.table(), player.getUUID()) -
                     empty means this player is not seated at that table, and the payload is dropped.
                     Prove this refusal with a game test that fails without it.
                  2. The point has to be on the cluster's own surface: build the TableTop the way
                     TableCameraView.surfaceOf does - from the board's seat count and
                     TableClusters.at(level, origin).turned() - and drop anything outside it.
                  3. The rate limit above, per player, on the server tick rather than on a clock.
                  4. Sending.to each of TableBroadcast.watchingNearby(level, payload.table()) with a
                     TablePointingPayload naming this player. Do not send it back to the sender:
                     their own arm is not drawn for them, and one fewer packet per player per
                     pointer at a full pod is worth having.""");
    }

    /**
     * Says a player has stopped pointing, for the paths that are not a payload: standing up,
     * leaving the seat, logging out, the session ending.
     * <p>Here rather than left to the client, because the client that stops pointing is the one
     * that disconnected and the clients that need to know are the other ones. An arm left pointing
     * at a table by somebody who went home is the kind of thing nobody reports and everybody sees.
     */
    public static void stopped(ServerPlayer player) {
        throw new UnsupportedOperationException("""
                Not written yet. Broadcast a TablePointingPayload with pointing = false for this
                player to whoever was watching the table they were at. Call it from the same places
                that already let a seat go - see Chairs.gotUp and the seat-release path in
                TableSeats - and from the player-disconnect lifecycle.""");
    }
}
