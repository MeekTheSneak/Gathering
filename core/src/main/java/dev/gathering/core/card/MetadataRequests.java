package dev.gathering.core.card;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Decides which printings a client still needs to be told about.
 *
 * <p>What a client knows about cards is memory only and is thrown away on disconnect, on
 * purpose: what one server said is not true of the next. The consequence is that after a
 * restart a card sitting in an inventory is a UUID and nothing else, so something has to ask
 * again - and the thing that asks runs every tick, which makes "ask once, not every tick"
 * the entire problem.
 *
 * <p>So the deciding lives here, where it can be checked, rather than in a tick handler
 * where a mistake is a request storm aimed at somebody else's server.
 *
 * <p>A printing that goes unanswered is retried, but slowly. The server may have been unable
 * to reach Scryfall for that moment, and never trying again would leave the card nameless
 * for the rest of the session.
 */
public final class MetadataRequests {

    /** How long an unanswered printing waits before it is worth asking about again. */
    public static final long RETRY_AFTER_MILLIS = 60_000L;

    private final Map<UUID, Long> asked = new HashMap<>();

    /**
     * The printings worth asking about now.
     *
     * @param held     every printing the client can currently see itself holding
     * @param known    whether the client already has this printing's metadata
     * @param now      a monotonic-enough millisecond clock
     * @param maximum  the most that may go in one request
     */
    public List<UUID> next(Collection<UUID> held, Predicate<UUID> known, long now, int maximum) {
        if (held == null || held.isEmpty() || maximum <= 0) {
            return List.of();
        }

        // Distinct and in a stable order, so the same tick twice does not produce two
        // different requests for the same cards.
        List<UUID> wanted = new ArrayList<>(maximum);
        for (UUID printing : new LinkedHashSet<>(held)) {
            if (printing == null || known.test(printing)) {
                continue;
            }
            Long previous = asked.get(printing);
            if (previous != null && now - previous < RETRY_AFTER_MILLIS) {
                continue;
            }
            wanted.add(printing);
            if (wanted.size() >= maximum) {
                break;
            }
        }

        for (UUID printing : wanted) {
            asked.put(printing, now);
        }
        return List.copyOf(wanted);
    }

    /** Called on disconnect, alongside the cache this is tracking. */
    public void clear() {
        asked.clear();
    }
}
