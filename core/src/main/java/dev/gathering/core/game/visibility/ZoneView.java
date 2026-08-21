package dev.gathering.core.game.visibility;

import dev.gathering.core.game.ZoneRef;
import java.util.List;

/**
 * One zone as one viewer sees it.
 *
 * <p>For a hidden zone the viewer is not entitled to, {@code cards} is empty and
 * {@code count} is the whole of what is sent. Not a list of placeholders - an empty list.
 * A placeholder per card would be a per-card payload addressed to a client with no
 * entitlement to per-card anything, and the number of placeholders is already the count.
 */
public record ZoneView(ZoneRef ref, int count, List<CardView> cards) {

    public ZoneView {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /** The library, and everyone else's hand: a number and nothing else. */
    public static ZoneView countOnly(ZoneRef ref, int count) {
        return new ZoneView(ref, count, List.of());
    }

    public boolean isCountOnly() {
        return cards.isEmpty() && count > 0;
    }
}
