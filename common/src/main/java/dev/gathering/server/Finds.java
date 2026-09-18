package dev.gathering.server;

import dev.gathering.core.sealed.FindingPace;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.RandomSource;

/**
 * What the world has given each player lately, so a machine cannot outrun the pace it was built for.
 * <p>The rates in {@code LootSource}, {@code CoinDrops} and {@code ArchiveDrops} are set against an
 * hour of somebody playing. They are not set against a wither farm, a re-triggered shrieker, a
 * respawned dragon or a weighted fishing rod, and at those rates any of those is the rarest thing in
 * the mod on tap. {@link FindingPace} is the arithmetic; this is where the counting lives.
 * <p><b>Kept across a logout.</b> It used to be thrown away with the player, on the reasoning that a
 * bucket drains while somebody is away so throwing it out says the same thing. That reasoning is
 * wrong, and wrong in the direction that matters: forgetting is an instant and complete drain rather
 * than a drain over time, so a farmer who had run the odds up to one in sixteen could relog - ten
 * seconds on a local server - and have the next one pay out for certain. The whole limit was
 * bypassable by the thing people do constantly in a session.
 * <p>So a bucket outlives its player, and is let go of when it has genuinely drained. Not saved to
 * disk: a server restart is a real interruption of the same kind the drain is about, and the file
 * would have to be written for every player who ever found anything.
 * <p>Server thread only: loot is rolled there.
 */
public final class Finds {

    /** One player's buckets, and when each was last looked at. */
    private static final Map<UUID, Map<FindingPace, double[]>> BUCKETS = new HashMap<>();

    /** How many players' buckets are kept before the drained ones are let go of. */
    private static final int MOST_KEPT = 256;

    private Finds() {
    }

    /**
     * Whether this find actually arrives, and counts it if it does.
     * <p>Everything arrives while a player is inside the burst, which is where ordinary play lives.
     * Past it a find has to win a roll that gets longer the further past it they are.
     *
     * @param player who is being given it, or null where nothing knows - which is not counted
     *               against anybody and is not thinned either
     */
    public static boolean arrives(UUID player, FindingPace kind, RandomSource random) {
        if (player == null || kind == null || random == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (BUCKETS.size() > MOST_KEPT && !BUCKETS.containsKey(player)) {
            // Somewhere to do the sweep that costs nothing on the ordinary path: a find, by somebody
            // the map has never seen, on a server that has seen a great many people.
            forgetTheDrained();
        }
        double[] bucket = BUCKETS.computeIfAbsent(player, who -> new EnumMap<>(FindingPace.class))
                .computeIfAbsent(kind, ignored -> new double[] {0.0, now});
        double had = kind.drained(bucket[0], now - (long) bucket[1]);
        bucket[1] = now;
        int oneIn = kind.oneIn(had);
        if (oneIn > 1 && random.nextInt(oneIn) != 0) {
            // Thinned. Not counted: what was never handed over is not something they have had, and
            // counting refusals would drive the bucket up on its own and close the tap for good.
            bucket[0] = had;
            return false;
        }
        bucket[0] = had + 1;
        return true;
    }

    /** How full one player's bucket is, for the in-world tests. */
    public static double had(UUID player, FindingPace kind) {
        Map<FindingPace, double[]> mine = BUCKETS.get(player);
        double[] bucket = mine == null ? null : mine.get(kind);
        return bucket == null ? 0.0 : kind.drained(bucket[0], System.currentTimeMillis() - (long) bucket[1]);
    }

    /**
     * Lets go of anybody whose buckets have all drained to nothing.
     * <p>How the map stays bounded, now that a player leaving is not what empties it. An empty bucket
     * and no bucket are the same answer, so there is nothing to lose by dropping it - and anybody
     * still inside a pace they have not drained keeps it, which is the point.
     */
    public static void forgetTheDrained() {
        BUCKETS.entrySet().removeIf(who -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<FindingPace, double[]> bucket : who.getValue().entrySet()) {
                if (bucket.getKey().drained(bucket.getValue()[0], now - (long) bucket.getValue()[1]) > 0) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Forgets one player, for a test that wants a fresh bucket.
     * <p>Named so, because nothing in the game may do this: a player leaving deliberately keeps
     * theirs, and emptying it on any ordinary event is how the limit was bypassable.
     */
    public static void forgetForTesting(UUID player) {
        BUCKETS.remove(player);
    }

    /** Forgets everybody, for a server that is stopping. */
    public static void clear() {
        BUCKETS.clear();
    }
}
