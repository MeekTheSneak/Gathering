package dev.gathering.core.card;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A set as Scryfall lists it, and the rule for which one a server means by "the current set".
 * <p>An admin who has not chosen a set means the one on the shelves, and Scryfall knows what
 * that is. Working it out here rather than shipping a set code means a server installed in
 * two years' time is still current on the day it starts, with nobody editing anything.
 * <p>What counts is a **premier** set that has actually come out on paper: an expansion or a
 * core set. That is the shape of release that gets draft, set, collector and play boosters,
 * which is the whole of what a shelf and a loot table can offer. Everything else Scryfall
 * lists - commander decks, masters sets, tokens, promos, the Arena-only sets - is either not
 * a booster release or not paper, and a shop pinned to one of them is a shop pinned to
 * something nobody would call the current set.
 * <p>Future sets are on the list too, months ahead of release. Today's date decides: a set
 * announced for December is not what a server in August should be selling.
 * <p>Pure.
 */
public record SetRelease(
        String code,
        String name,
        String type,
        String releasedOn,
        boolean digital,
        int cardCount,
        int printedSize) {

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
        cardCount = Math.max(0, cardCount);
        printedSize = Math.max(0, printedSize);
    }

    /**
     * How many cards the set was printed as: the number on the bottom of every card in it.
     * <p>Not the same as {@link #cardCount}, and the difference is the whole of what "a
     * complete set" means. A modern release has a few hundred numbered cards and then several
     * hundred more sharing the same set code - borderless, extended art, showcase, promos,
     * the buy-a-box card - which are numbered above the printed size and which nobody counts
     * against you. A player who owns one of every card the set says it has owns the set.
     * <p>Falls back to the full count for the older sets Scryfall gives no printed size for,
     * where the two were the same thing anyway because nobody was printing variants yet.
     */
    public int sizeOfTheSet() {
        return printedSize > 0 ? printedSize : cardCount;
    }

    /**
     * Whether a card with this collector number is one of the numbered set.
     * <p>Collector numbers are text, not numbers: a card can be "103a" or a star. What counts
     * is the number it starts with, and a number that runs past the printed size - or one
     * that does not start with a digit at all - is one of the extras.
     */
    public boolean numbers(String collectorNumber) {
        int number = leadingNumber(collectorNumber);
        return number >= 1 && number <= sizeOfTheSet();
    }

    /** The number a collector number starts with, or zero where it starts with anything else. */
    public static int leadingNumber(String collectorNumber) {
        if (collectorNumber == null) {
            return 0;
        }
        int end = 0;
        while (end < collectorNumber.length() && Character.isDigit(collectorNumber.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(collectorNumber.substring(0, end));
        } catch (NumberFormatException tooLong) {
            // A collector number of forty digits is not a card, it is somebody's typo.
            return 0;
        }
    }

    /** Whether this is the kind of set that gets boosters printed for it, on paper. */
    public boolean isPremier() {
        return !digital && PREMIER_TYPES.contains(type) && SetCode.isOne(code);
    }

    /**
     * Whether this had come out by a given day.
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
        return recent(sets, today, 1).stream().findFirst();
    }

    /**
     * The last few premier sets, newest first.
     * <p>What a server drawing its packs from more than one release is drawing from. Twelve
     * is about three years of Magic, which is the span a player has any feel for.
     *
     * @param howMany at most this many; fewer where the list is shorter
     */
    public static List<SetRelease> recent(List<SetRelease> sets, String today, int howMany) {
        if (sets == null || howMany <= 0) {
            return List.of();
        }
        return sets.stream()
                .filter(set -> set.isPremier() && set.wasOutBy(today))
                .sorted(NEWEST_FIRST)
                .limit(howMany)
                .toList();
    }
}
