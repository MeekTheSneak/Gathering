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
 * <p>Safe anywhere on the server: the list is read if it is here, and a miss is an absence - never a
 * wait, and never a request. A pack is named as it is made, in a loot roll and on a shopkeeper's
 * shelf, and asking Scryfall from there queued a download of the whole list for every pack made while
 * it was missing. Something else asks for the list: the server's start, a restock, a press on a set
 * count.
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
        Map<String, SetRelease> sets = cards == null ? null : cards.knownSets();
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
