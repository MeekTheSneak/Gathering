package dev.gathering.core.card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which files of a cache to delete once it has grown past its limit: the least recently used first,
 * until it is back under a lower mark.
 * <p>Two marks rather than one, so a cache sitting at its limit is not trimmed by one file every time
 * somebody looks at a new card. Pure.
 */
public final class CacheTrim {

    /** One file: what to call it, how big it is, and when it was last read or written. */
    public record Entry(String name, long bytes, long usedAt) {
    }

    private CacheTrim() {
    }

    /**
     * @param over  the size past which anything is deleted at all
     * @param under the size to delete down to, once past it
     * @return the entries to delete, least recently used first
     */
    public static List<Entry> toDelete(List<Entry> entries, long over, long under) {
        List<Entry> deleting = new ArrayList<>();
        if (entries == null) {
            return deleting;
        }
        long total = entries.stream().mapToLong(entry -> Math.max(0, entry.bytes())).sum();
        if (total <= over) {
            return deleting;
        }
        List<Entry> oldestFirst = new ArrayList<>(entries);
        oldestFirst.sort(Comparator.comparingLong(Entry::usedAt).thenComparing(Entry::name));
        for (Entry entry : oldestFirst) {
            if (total <= under) {
                break;
            }
            deleting.add(entry);
            total -= Math.max(0, entry.bytes());
        }
        return deleting;
    }
}
