package dev.gathering.client;

import net.minecraft.client.Minecraft;

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
     * <p>About a third of a card. Below that the arm has not visibly moved and the packet is a
     * packet for nothing; above it, the interpolation covers the gap.
     */
    private static final double WORTH_SAYING = 8;

    /** And how many ticks may pass between packets while the point is moving. */
    private static final int AT_MOST_EVERY = 4;

    private TablePointSender() {
    }

    /**
     * Called from the client tick.
     * <p>Sends nothing at all in almost every session: a player who is not seated at a table with
     * the board open has no pointer, and this has to be cheap about saying so.
     */
    public static void tick(Minecraft client) {
        throw new UnsupportedOperationException("""
                Not written yet. Send a TablePointPayload when all of these hold:
                  - the player is seated at a table (ClientTableState.seatedAt)
                  - the table screen is open and the cursor is over the felt
                  - the point has moved by WORTH_SAYING since the last one sent, and at least
                    AT_MOST_EVERY ticks have passed
                Send one with pointing = false, once, when any of those stops holding: the screen
                closed, the cursor left the felt, the player stood up. Once, not every tick - a
                stream of "not pointing" is the same packet storm by another name.
                Call it from ClientTicks, which is where the client's per-tick work already lives.""");
    }

    /** Forgets what was last sent, so a new table does not inherit the last one's point. */
    public static void clear() {
        throw new UnsupportedOperationException(
                "Not written yet - and if it holds anything, name it in ClientState.forgetTheServer.");
    }
}
