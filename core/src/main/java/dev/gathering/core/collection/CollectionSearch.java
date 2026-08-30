package dev.gathering.core.collection;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finding one card in a collection of ten thousand.
 *
 * <p>A binder you cannot search is a box. The buttons answer the questions somebody asks
 * without thinking - what red cards have I got, what is in this set - and the box answers the
 * rest through {@link CardSearch}, which is Scryfall's syntax because everybody who plays this
 * game has already typed it somewhere else. Bare words still just look for a name, so nothing
 * has to be learned before the box is useful.
 *
 * <p>Every filter is "and". Typing a word and picking a color means both, which is what
 * anybody would expect and the only rule that does not need explaining.
 *
 * <p>A card whose details have not been fetched yet is still in the collection and still
 * counted. It matches a search with nothing in it and no other, because nothing true can be
 * said about it - and it sorts to the end, so a collection that is still loading reads as
 * itself with a tail rather than as a jumble.
 *
 * <p>Pure. Nothing here fetches anything.
 */
public final class CollectionSearch {

    private CollectionSearch() {
    }

    /**
     * One line of a collection: a card, how many, and what is known about it.
     *
     * @param about the card's details, or null where they have not been fetched
     */
    public record Row(CardIdentity card, int count, CardMetadata about) {

        public boolean isKnown() {
            return about != null;
        }

        public String name() {
            return about == null ? "" : about.name();
        }
    }

    /** What to order a collection by. Every one of them settles ties by name. */
    public enum Sort {
        NAME,
        SET,
        RARITY,
        COLOR,
        COUNT
    }

    /**
     * What somebody is looking for.
     *
     * @param text     words that must all appear somewhere in the card - its name, its type
     *                 line, its set. Blank matches everything.
     * @param setCode  one set, or blank for all of them
     * @param colors  single letters; a card matches when it is at least these colors, so
     *                 asking for W and U finds Azorius cards and not mono-white ones. Empty
     *                 matches everything, and asking for none of them - "C" - finds the
     *                 colorless.
     * @param rarity   one rarity, or null for all of them
     * @param type     a word from the type line - "creature", "instant", "equipment"
     */
    public record Query(
            String text,
            String setCode,
            Set<String> colors,
            Rarity rarity,
            String type,
            Sort sort,
            boolean descending) {

        public Query {
            text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
            colors = colors == null ? Set.of() : upperCased(colors);
            type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            sort = sort == null ? Sort.NAME : sort;
        }

        /** Everything, by name. What a collection looks like when it is first opened. */
        public static Query everything() {
            return new Query("", "", Set.of(), null, "", Sort.NAME, false);
        }

        /** Whether this asks anything at all, or is just an order to put things in. */
        public boolean filtersAnything() {
            return !text.isEmpty() || !setCode.isEmpty() || !colors.isEmpty()
                    || rarity != null || !type.isEmpty();
        }

        public Query orderedBy(Sort newSort, boolean newDescending) {
            return new Query(text, setCode, colors, rarity, type, newSort, newDescending);
        }

        public Query searchingFor(String newText) {
            return new Query(newText, setCode, colors, rarity, type, sort, descending);
        }
    }

    /** The rows that match, in the order they were asked for. */
    public static List<Row> run(List<Row> rows, Query query) {
        Query asked = query == null ? Query.everything() : query;
        List<Row> found = new ArrayList<>();
        if (rows != null) {
            for (Row row : rows) {
                if (row != null && row.count() > 0 && matches(row, asked)) {
                    found.add(row);
                }
            }
        }
        found.sort(order(asked));
        return List.copyOf(found);
    }

    /** Whether one card answers a search. */
    public static boolean matches(Row row, Query query) {
        if (row == null) {
            return false;
        }
        Query asked = query == null ? Query.everything() : query;
        CardMetadata about = row.about();
        if (about == null) {
            // Nothing is known about it, so nothing can be said to be true of it. It survives
            // a search with no filters and no other, which is the honest answer rather than
            // hiding a card somebody owns or claiming it is red.
            return !asked.filtersAnything();
        }
        if (!asked.setCode().isEmpty()
                && !asked.setCode().equalsIgnoreCase(about.setCode())) {
            return false;
        }
        if (asked.rarity() != null && about.rarity() != asked.rarity()) {
            return false;
        }
        if (!asked.type().isEmpty()
                && !lower(about.typeLine()).contains(asked.type())) {
            return false;
        }
        if (!isColors(about, asked.colors())) {
            return false;
        }
        return CardSearch.matches(about, row.count(), CardSearch.parse(asked.text()));
    }

    // ------------------------------------------------------------- the rules

    /**
     * Whether a card is at least these colors.
     *
     * <p>"C" on its own means colorless, which is not a color a card has but the absence of
     * every one - so it is asked as its own question rather than looked for in the list.
     */
    private static boolean isColors(CardMetadata about, Set<String> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        Set<String> has = new LinkedHashSet<>();
        for (String color : about.colors()) {
            has.add(color.trim().toUpperCase(Locale.ROOT));
        }
        for (String color : wanted) {
            if (color.equals("C")) {
                if (!has.isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!has.contains(color)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The order to put them in.
     *
     * <p>Every order ends with the same two tie-breaks - name, then the card's own key - so a
     * collection sorted by rarity does not shuffle its commons about between two openings of
     * the same screen.
     */
    private static Comparator<Row> order(Query query) {
        Comparator<Row> by = switch (query.sort()) {
            case NAME -> Comparator.comparing(row -> lower(row.name()));
            case SET -> Comparator.comparing(
                    (Row row) -> row.isKnown() ? lower(row.about().setCode()) : "")
                    .thenComparing(row -> collectorNumber(row));
            case RARITY -> Comparator.comparingInt(CollectionSearch::rarityRank).reversed();
            case COLOR -> Comparator.comparing(CollectionSearch::colorKey);
            case COUNT -> Comparator.comparingInt(Row::count).reversed();
        };
        if (query.descending()) {
            by = by.reversed();
        }
        // Unknown cards last, whichever way round the rest are. A collection still fetching
        // its details reads as itself with a tail rather than as a jumble.
        Comparator<Row> knownFirst = Comparator.comparing((Row row) -> row.isKnown() ? 0 : 1);
        return knownFirst
                .thenComparing(by)
                .thenComparing(row -> lower(row.name()))
                .thenComparing(row -> row.card().cacheKey());
    }

    /** Where a rarity sits, most wanted highest. */
    private static int rarityRank(Row row) {
        if (!row.isKnown()) {
            return -1;
        }
        return switch (row.about().rarity()) {
            case MYTHIC -> 5;
            case SPECIAL, BONUS -> 4;
            case RARE -> 3;
            case UNCOMMON -> 2;
            case COMMON -> 1;
            case UNKNOWN -> 0;
        };
    }

    /**
     * A card's colors as one sortable string, in Magic's own order.
     *
     * <p>WUBRG rather than alphabetical, because that is the order every player already reads
     * colors in and a list that puts blue after black looks broken to them.
     */
    private static String colorKey(Row row) {
        if (!row.isKnown()) {
            return "~";
        }
        Set<String> has = new LinkedHashSet<>();
        for (String color : row.about().colors()) {
            has.add(color.trim().toUpperCase(Locale.ROOT));
        }
        if (has.isEmpty()) {
            // Colorless after the colors rather than before them: it is where a player
            // looks for artifacts and lands, which is the end of the binder.
            return "z";
        }
        StringBuilder key = new StringBuilder();
        // How many colors first, so the mono-colored cards are together and the gold ones
        // follow, which is how anybody lays a collection out.
        key.append((char) ('0' + Math.min(9, has.size())));
        // The color where a card has it, and a character that sorts after every letter
        // where it does not - so a white card comes before a blue one because W beats the
        // placeholder in the first position, which is what WUBRG order actually is.
        for (char color : new char[] {'W', 'U', 'B', 'R', 'G'}) {
            key.append(has.contains(String.valueOf(color)) ? color : '~');
        }
        return key.toString();
    }

    private static String collectorNumber(Row row) {
        if (!row.isKnown()) {
            return "";
        }
        String number = row.about().collectorNumber();
        if (number == null) {
            return "";
        }
        // Padded so 2 comes before 10. Collector numbers carry letters as well as digits, so
        // the digits are padded where they are and the rest is left alone.
        StringBuilder padded = new StringBuilder();
        int digits = 0;
        while (digits < number.length() && Character.isDigit(number.charAt(digits))) {
            digits++;
        }
        for (int pad = digits; pad < 6; pad++) {
            padded.append('0');
        }
        padded.append(number);
        return padded.toString().toLowerCase(Locale.ROOT);
    }

    private static Set<String> upperCased(Set<String> colors) {
        Set<String> kept = new LinkedHashSet<>();
        for (String color : colors) {
            if (color != null && !color.isBlank()) {
                kept.add(color.trim().toUpperCase(Locale.ROOT));
            }
        }
        return java.util.Collections.unmodifiableSet(kept);
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
