package dev.gathering.core.card;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A set as Scryfall lists it, and the rule for which one a server means by "the current set".
 *
 * <p>An admin who has not chosen a set means the one on the shelves, and Scryfall knows what
 * that is. Working it out here rather than shipping a set code means a server installed in
 * two years' time is still current on the day it starts, with nobody editing anything.
 *
 * <p>What counts is a **premier** set that has actually come out on paper: an expansion or a
 * core set. That is the shape of release that gets draft, set, collector and play boosters,
 * which is the whole of what a shelf and a loot table can offer. Everything else Scryfall
 * lists - commander decks, masters sets, tokens, promos, the Arena-only sets - is either not
 * a booster release or not paper, and a shop pinned to one of them is a shop pinned to
 * something nobody would call the current set.
 *
 * <p>Future sets are on the list too, months ahead of release. Today's date decides: a set
 * announced for December is not what a server in August should be selling.
 *
 * <p>Pure.
 */
public record SetRelease(
        String code,
        String name,
        String type,
        String releasedOn,
        boolean digital,
        int cardCount) {

    /** The set types a booster is printed for. */
    private static final List<String> PREMIER_TYPES = List.of("expansion", "core");

    /** Newest first, and by code where two came out on the same day, so ties do not wander. */
    private static final Comparator<SetRelease> NEWEST_FIRST =
            Comparator.comparing(SetRelease::releasedOn).reversed()
                    .thenComparing(SetRelease::code);

    public SetRelease {
        code = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        name = name == null ? "" : name;
        type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        releasedOn = releasedOn == null ? "" : releasedOn.trim();
    }

    /** Whether this is the kind of set that gets boosters printed for it, on paper. */
    public boolean isPremier() {
        return !digital && PREMIER_TYPES.contains(type) && SetCode.isOne(code);
    }

    /**
     * Whether this had come out by a given day.
     *
     * <p>Dates compare as text because Scryfall writes them as {@code YYYY-MM-DD}, where
     * that is the same answer as comparing them as dates and does not need a calendar.
     */
    public boolean wasOutBy(String today) {
        Objects.requireNonNull(today, "today");
        return !releasedOn.isEmpty() && releasedOn.compareTo(today) <= 0;
    }

    /**
     * The newest premier set that has come out, out of everything Scryfall lists.
     *
     * @param today the day to judge against, written as Scryfall writes a release date
     * @return empty if the list holds nothing that has come out yet, which is a list that
     *         did not arrive rather than a state the real one is ever in
     */
    public static Optional<SetRelease> current(List<SetRelease> sets, String today) {
        if (sets == null) {
            return Optional.empty();
        }
        return sets.stream()
                .filter(set -> set.isPremier() && set.wasOutBy(today))
                .sorted(NEWEST_FIRST)
                .findFirst();
    }
}
