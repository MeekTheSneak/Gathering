package dev.gathering.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Saying no once, however many times it was asked.
 * <p>A verb applied to a selection sends one move per card, on purpose: nothing about a
 * selection reaches the server, so a selection can do nothing an ordinary sequence of moves
 * could not. The cost of that - and it is worth paying - is that a selection the table refuses
 * is refused once per card. Sending forty cards to the graveyard in a game that has finished
 * put forty identical lines in the chat, which is not forty times as informative as one; it is
 * less, because the line that says what went wrong is now the thing burying itself.
 * <p>So the server stops repeating itself. The first refusal goes straight out. An identical
 * one arriving while that is still fresh is counted instead, and when the run ends - a
 * different refusal, or a moment's quiet - the count follows it: "and 39 more like that."
 * <p>This is not the server being told about selections. It is the server not saying the same
 * sentence to the same person twice in a row inside a tenth of a second, which is true of
 * mashing a key as well and was worth doing on its own.
 * <p>Server thread only.
 */
public final class Refusals {

    /**
     * How long an identical refusal is folded into the one before it.
     * <p>Two ticks. Long enough to cover a burst of moves from one gesture, which all arrive
     * in the same tick or the next, and short enough that two separate presses a second apart
     * are two separate answers - because they were two separate questions.
     */
    static final int FOLDING_TICKS = 2;

    /** What was last said to somebody, and how much has piled up behind it. */
    private static final class Run {

        private final String reason;
        private int swallowed;
        private int lastTick;

        private Run(String reason, int tick) {
            this.reason = reason;
            this.lastTick = tick;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    /**
     * A ceiling, so a server that has gone wrong cannot grow this without end.
     * <p>One entry per connected player, dropped on disconnect, so in practice this is the
     * player count. The bound is what makes that a fact rather than a belief.
     */
    private static final int MOST_REMEMBERED = 4096;

    private Refusals() {
    }

    /**
     * Tells this player the table said no, folding a repeat into the one before it.
     * <p>Always says something the first time. A refusal that could be swallowed entirely
     * would be a table that silently ignores you, which is the one thing worse than saying
     * the same thing twice.
     */
    public static void tell(ServerPlayer player, String reason) {
        if (player == null || reason == null || reason.isBlank()) {
            return;
        }
        int now = player.server.getTickCount();
        UUID who = player.getUUID();
        Run run = RUNS.get(who);

        if (run != null && run.reason.equals(reason) && now - run.lastTick <= FOLDING_TICKS) {
            run.swallowed++;
            run.lastTick = now;
            // Re-armed, so the tally lands after the burst rather than in the middle of it.
            armTheTally(player, who);
            return;
        }

        // A different thing to say. Whatever was piling up behind the last one goes out first,
        // so the tally never arrives after the sentence it is about has scrolled away.
        settle(player, who);
        if (RUNS.size() >= MOST_REMEMBERED && !RUNS.containsKey(who)) {
            player.sendSystemMessage(Component.literal(reason));
            return;
        }
        RUNS.put(who, new Run(reason, now));
        player.sendSystemMessage(Component.literal(reason));
        armTheTally(player, who);
    }

    /**
     * Asks for the tally to be said a few ticks from now, once the burst is over.
     * <p>Through {@link ServerTicks} because that is the mod's one real tick hook, and because
     * {@code server.execute} would run this inline and say "and 0 more" before the rest of the
     * burst had arrived.
     */
    private static void armTheTally(ServerPlayer player, UUID who) {
        ServerTicks.on(key(who), player.server.getTickCount() + FOLDING_TICKS + 1, () -> {
            ServerPlayer still = player.server.getPlayerList().getPlayer(who);
            if (still != null) {
                settle(still, who);
            } else {
                RUNS.remove(who);
            }
        });
    }

    /** Says what piled up behind the last refusal, if anything did, and closes the run. */
    private static void settle(ServerPlayer player, UUID who) {
        Run run = RUNS.remove(who);
        if (run == null || run.swallowed == 0) {
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.gathering.table.and_more_like_that", run.swallowed));
    }

    /** How many refusals are being held back for this player, which is what a test asks. */
    public static int swallowedFor(UUID who) {
        Run run = RUNS.get(who);
        return run == null ? 0 : run.swallowed;
    }

    /** Forgotten on disconnect: it is counted in this server's ticks and in no other. */
    public static void forget(UUID who) {
        if (who == null) {
            return;
        }
        RUNS.remove(who);
        ServerTicks.forget(key(who));
    }

    /** Drops everything, for a server that is stopping and for a test that wants a clean slate. */
    public static void clear() {
        for (UUID who : Map.copyOf(RUNS).keySet()) {
            ServerTicks.forget(key(who));
        }
        RUNS.clear();
    }

    /** This player's slot in the tick queue, distinct from every other user of it. */
    private static Object key(UUID who) {
        return "gathering:refusals:" + who;
    }
}
