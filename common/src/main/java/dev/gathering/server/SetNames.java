package dev.gathering.server;

import dev.gathering.core.card.SetRelease;
import dev.gathering.service.CardDataService;
import java.util.Locale;
import java.util.Map;

/**
 * What to call a set when telling a player about it.
 * <p>Three letters is how Magic's data names a set and how this mod stores one, and it is not how
 * anybody talks: the owner's point is that most players know "Bloomburrow" and not "blb". So
 * anything said to a player says the name, and the code stays where it belongs - in components, in
 * commands somebody types, and in the log.
 * <p>Falls back to the code in capitals where the sets have not been read yet, which is the first
 * seconds of a server and a server with no network. That is the same answer as before rather than a
 * blank, and it comes back right on its own once the list arrives.
 * <p>Server thread safe: the set list is read without waiting, and a miss is an absence rather than
 * a stall. Never call this from a loot roll or a render - it is for chat and command output.
 */
public final class SetNames {

    private SetNames() {
    }

    /** The set's own name, or its code in capitals where nothing here knows it yet. */
    public static String of(String code) {
        String asked = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (asked.isEmpty()) {
            return "";
        }
        CardDataService cards = CardDataService.active().orElse(null);
        Map<String, SetRelease> sets = cards == null ? null : cards.allSets().getNow(null);
        SetRelease set = sets == null ? null : sets.get(asked);
        return set == null || set.name() == null || set.name().isBlank()
                ? asked.toUpperCase(Locale.ROOT)
                : set.name();
    }

    /** The same, for somewhere that already holds the set's own record. */
    public static String of(SetRelease set, String code) {
        return set == null || set.name() == null || set.name().isBlank() ? of(code) : set.name();
    }
}
