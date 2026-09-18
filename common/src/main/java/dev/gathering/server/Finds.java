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
 * <p>Kept in memory and not saved. A player who logs out has stopped playing, and coming back to a
 * bucket that drained while they were away is the same answer the drain would have given anyway -
 * so persisting it would only matter to somebody who logs out and straight back in, which costs
 * them more time than it saves.
 * <p>Server thread only: loot is rolled there.
 */
public final class Finds {

    /** One player's buckets, and when each was last looked at. */
    private static final Map<UUID, Map<FindingPace, double[]>> BUCKETS = new HashMap<>();

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

    /** Forgets one player, who has gone. */
    public static void forget(UUID player) {
        BUCKETS.remove(player);
    }

    /** Forgets everybody, for a server that is stopping. */
    public static void clear() {
        BUCKETS.clear();
    }
}
