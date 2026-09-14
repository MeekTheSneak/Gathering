package dev.gathering.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How much of something each player may ask the server for, refilling over time.
 * <p>A bucket per player: it holds up to a burst, refills at a steady rate, and each request
 * spends from it. A person playing spends a little at a time and never notices; a client
 * sending as fast as a socket allows runs it dry and is refused until it refills, instead of
 * making the server do - and send everybody - work without end.
 * <p>Server thread only.
 */
public final class ActionBudget {

    /** Requests that queue a card lookup on the one card worker every player shares. */
    public static final ActionBudget CARD_LOOKUPS = new ActionBudget(2, 8);

    /** Requests that look up every printing in a set: many pages each. */
    public static final ActionBudget WHOLE_SETS = new ActionBudget(0.1, 2);

    /** Table requests beyond moves - dice, reveals, random discards - each sent to the table. */
    public static final ActionBudget TABLE_REQUESTS = new ActionBudget(5, 20);

    /** Forgets every shared budget, for a server that is stopping. */
    public static void clearShared() {
        CARD_LOOKUPS.clear();
        WHOLE_SETS.clear();
        TABLE_REQUESTS.clear();
    }

    private final double perSecond;
    private final double burst;
    private final Map<UUID, double[]> buckets = new HashMap<>();

    /**
     * @param perSecond how much refills each second
     * @param burst     the most that can be spent at once, and what a bucket starts with
     */
    public ActionBudget(double perSecond, double burst) {
        this.perSecond = perSecond;
        this.burst = burst;
    }

    /** Spends this much for this player if they have it, and says whether they did. */
    public boolean spend(UUID player, double amount) {
        long now = System.nanoTime();
        double[] bucket = buckets.computeIfAbsent(player, ignored -> new double[] {burst, now});
        double refilled = Math.min(burst, bucket[0] + (now - bucket[1]) / 1e9 * perSecond);
        bucket[1] = now;
        if (refilled < amount) {
            bucket[0] = refilled;
            return false;
        }
        bucket[0] = refilled - amount;
        if (buckets.size() > 1024) {
            // Forgotten wholesale past a bound: a full bucket is what a forgotten one starts as.
            buckets.clear();
        }
        return true;
    }

    /** Forgets one player, who has gone. */
    public void forget(UUID player) {
        buckets.remove(player);
    }

    /** Forgets one player in every shared budget. */
    public static void forgetShared(UUID player) {
        CARD_LOOKUPS.forget(player);
        WHOLE_SETS.forget(player);
        TABLE_REQUESTS.forget(player);
    }

    /** Forgets everybody, for a server that is stopping. */
    public void clear() {
        buckets.clear();
    }
}
