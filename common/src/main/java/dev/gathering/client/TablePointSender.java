package dev.gathering.client;

import dev.gathering.network.TablePointPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Telling the server where this player is pointing, when it is worth telling.
 * <p>On the client tick, not on mouse movement. A packet per mouse event is a hundred a second per
 * player for an animation the eye reads at five, and the smoothness comes from the interpolation in
 * {@link ClientTablePointing} rather than from the rate.
 * <p>The point is the one the table screen has already worked out. {@link TablePointer} answers
 * from matrices captured while the world was drawn and is only true inside a frame, so this reads
 * what the screen found rather than running the picker from a tick - which would answer from the
 * last frame drawn before whatever happened since.
 * <p>Client-only.
 */
public final class TablePointSender {

    /**
     * How far the point must move before it is worth a packet, in surface units.
     * <p>About a third of a card, on a table ten thousand units across. Below that the arm has not
     * visibly moved and the packet is a packet for nothing; above it, the interpolation covers the
     * gap.
     */
    private static final double WORTH_SAYING = 300;

    /** And how many ticks may pass between packets while the point is moving. */
    private static final int AT_MOST_EVERY = 4;

    /** Where the screen last found the cursor on the felt, written from the frame that found it. */
    private static volatile BlockPos hoveredTable;

    private static volatile double hoveredX;

    private static volatile double hoveredY;

    /** What was last sent, so a still cursor and a closed screen both cost nothing. */
    private static BlockPos sentTable;

    private static double sentX;

    private static double sentY;

    private static int ticksSinceSent;

    private TablePointSender() {
    }

    /**
     * Where the cursor is on the felt this frame, from the screen that worked it out.
     * <p>A null table means the cursor is not on any felt - off the edge, or over a menu - which
     * is a real answer and the one that puts the arm down.
     */
    public static void hovering(BlockPos table, double surfaceX, double surfaceY) {
        hoveredTable = table == null ? null : table.immutable();
        hoveredX = surfaceX;
        hoveredY = surfaceY;
    }

    /** The cursor has left the felt, or the screen it was on has closed. */
    public static void notHovering() {
        hoveredTable = null;
    }

    /**
     * Called from the client tick.
     * <p>Sends nothing at all in almost every session: a player who is not seated at a table with
     * the board open has no pointer, and this has to be cheap about saying so.
     */
    public static void tick(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        ticksSinceSent++;
        BlockPos table = hoveredTable;
        // No screen means no cursor, which the owner settled: seated with the table closed is a
        // body at rest, still holding its cards.
        if (table == null || client.screen == null) {
            stop();
            return;
        }
        if (!table.equals(sentTable)) {
            say(table, hoveredX, hoveredY);
            return;
        }
        if (ticksSinceSent < AT_MOST_EVERY) {
            return;
        }
        if (Math.abs(hoveredX - sentX) < WORTH_SAYING && Math.abs(hoveredY - sentY) < WORTH_SAYING) {
            return;
        }
        say(table, hoveredX, hoveredY);
    }

    /**
     * Says the pointer has stopped, once.
     * <p>Once, not every tick: a stream of "not pointing" is the same packet storm the cadence
     * above exists to avoid, sent by a player who is doing nothing at all.
     */
    private static void stop() {
        if (sentTable == null) {
            return;
        }
        ClientNetworking.send(new TablePointPayload(sentTable, 0, 0, false));
        sentTable = null;
        ticksSinceSent = 0;
    }

    private static void say(BlockPos table, double x, double y) {
        ClientNetworking.send(
                new TablePointPayload(table, (float) x, (float) y, true));
        sentTable = table;
        sentX = x;
        sentY = y;
        ticksSinceSent = 0;
    }

    /** Forgets what was last sent, so a new table does not inherit the last one's point. */
    public static void clear() {
        hoveredTable = null;
        sentTable = null;
        ticksSinceSent = 0;
    }
}
