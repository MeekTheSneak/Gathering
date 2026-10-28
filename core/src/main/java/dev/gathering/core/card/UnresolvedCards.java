package dev.gathering.core.card;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Printings a client asked about and was not told a name for, and why.
 * <p>Two different answers used to look identical: a card the server looked up and Scryfall
 * has never heard of, and a card the server could not look up at all because Scryfall was
 * down or rate limiting. Both left the client with no summary, so both read "Loading" for as
 * long as anybody looked - a promise that for the first kind was never going to be kept.
 * <p>They are kept apart here because they call for opposite things:
 * <ul>
 * <li><b>Missing</b> is an answer. It is not asked about again for a while, so a
 * card nobody has ever printed does not cost a lookup every minute of every session.</li>
 * <li><b>Unavailable</b> is not. It is shown, so the player knows it is not their card
 * that is wrong, and it never stops the next retry - an outage must not become a permanent
 * "no such card".</li>
 * </ul>
 * <p>Bounded, and forgotten on disconnect along with everything else a server said.
 */
public final class UnresolvedCards {

    /** Why a printing has no name. */
    public enum Reason {
        /** Looked up, and nothing is called that. */
        MISSING,
        /** Could not be looked up just now. */
        UNAVAILABLE
    }

    /**
     * How long "no such card" is believed.
     * <p>Not for ever. Scryfall adds printings, and a server's cache can be cleared, so an
     * answer from an hour ago is worth asking about again - just not every minute.
     */
    public static final long MISSING_FOR_MILLIS = 30L * 60L * 1000L;

    /**
     * The most remembered at once.
     * <p>A client is only ever told about printings it asked for, and it asks about what it
     * can see, but a server answering with invented ids must not be a way to grow this without
     * limit. Oldest first out.
     */
    public static final int MOST_REMEMBERED = 4096;

    private record Entry(Reason reason, long at) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** These printings were looked up and do not exist. */
    public void missing(Collection<UUID> printings, long now) {
        remember(printings, Reason.MISSING, now);
    }

    /**
     * These printings could not be looked up.
     * <p>Never downgrades a missing answer that is still believed: a later outage says nothing
     * new about a card already known not to exist.
     */
    public void unavailable(Collection<UUID> printings, long now) {
        if (printings == null) {
            return;
        }
        for (UUID printing : printings) {
            Entry was = printing == null ? null : entries.get(printing);
            if (was != null && was.reason() == Reason.MISSING && now - was.at() < MISSING_FOR_MILLIS) {
                continue;
            }
            put(printing, new Entry(Reason.UNAVAILABLE, now));
        }
    }

    /** A name arrived after all, which settles it whatever was said before. */
    public void found(UUID printing) {
        if (printing != null) {
            entries.remove(printing);
        }
    }

    /** Why this printing has no name, if a reason is known and still believed. */
    public Optional<Reason> reasonFor(UUID printing, long now) {
        Entry entry = printing == null ? null : entries.get(printing);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.reason() == Reason.MISSING && now - entry.at() >= MISSING_FOR_MILLIS) {
            entries.remove(printing);
            return Optional.empty();
        }
        return Optional.of(entry.reason());
    }

    /**
     * Whether asking about this printing now would be asking a question already answered.
     * <p>True only for a missing answer still believed. An unavailable one is exactly what a
     * retry is for.
     */
    public boolean alreadyAnswered(UUID printing, long now) {
        return reasonFor(printing, now).orElse(null) == Reason.MISSING;
    }

    public int size() {
        return entries.size();
    }

    /** Called on disconnect: what one server could not find, the next one might. */
    public void clear() {
        entries.clear();
    }

    private void remember(Collection<UUID> printings, Reason reason, long now) {
        if (printings == null) {
            return;
        }
        for (UUID printing : printings) {
            put(printing, new Entry(reason, now));
        }
    }

    private void put(UUID printing, Entry entry) {
        if (printing == null) {
            return;
        }
        // Moved to the newest end, so eviction takes whatever has gone longest unrefreshed.
        entries.remove(printing);
        entries.put(printing, entry);
        Iterator<UUID> oldest = entries.keySet().iterator();
        while (entries.size() > MOST_REMEMBERED && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
