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
 * <p>A binder you cannot search is a box. What people actually ask of one is narrow and
 * always the same: what red cards have I got, how many of this rare, what is in this set,
 * where is that thing called something-Bolt - so those are the questions, and there is no
 * query language to learn.
 *
 * <p>Every filter is "and". Typing a word and picking a colour means both, which is what
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
        COLOUR,
        COUNT
    }

    /**
     * What somebody is looking for.
     *
     * @param text     words that must all appear somewhere in the card - its name, its type
     *                 line, its set. Blank matches everything.
     * @param setCode  one set, or blank for all of them
     * @param colours  single letters; a card matches when it is at least these colours, so
     *                 asking for W and U finds Azorius cards and not mono-white ones. Empty
     *                 matches everything, and asking for none of them - "C" - finds the
     *                 colourless.
     * @param rarity   one rarity, or null for all of them
     * @param type     a word from the type line - "creature", "instant", "equipment"
     */
    public record Query(
            String text,
            String setCode,
            Set<String> colours,
            Rarity rarity,
            String type,
            Sort sort,
            boolean descending) {

        public Query {
            text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
            colours = colours == null ? Set.of() : upperCased(colours);
            type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            sort = sort == null ? Sort.NAME : sort;
        }

        /** Everything, by name. What a collection looks like when it is first opened. */
        public static Query everything() {
            return new Query("", "", Set.of(), null, "", Sort.NAME, false);
        }

        /** Whether this asks anything at all, or is just an order to put things in. */
        public boolean filtersAnything() {
            return !text.isEmpty() || !setCode.isEmpty() || !colours.isEmpty()
                    || rarity != null || !type.isEmpty();
        }

        public Query orderedBy(Sort newSort, boolean newDescending) {
            return new Query(text, setCode, colours, rarity, type, newSort, newDescending);
        }

        public Query searchingFor(String newText) {
            return new Query(newText, setCode, colours, rarity, type, sort, descending);
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
        if (!isColours(about, asked.colours())) {
            return false;
        }
        return saysAll(about, asked.text());
    }

    // ------------------------------------------------------------- the rules

    /**
     * Whether a card is at least these colours.
     *
     * <p>"C" on its own means colourless, which is not a colour a card has but the absence of
     * every one - so it is asked as its own question rather than looked for in the list.
     */
    private static boolean isColours(CardMetadata about, Set<String> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        Set<String> has = new LinkedHashSet<>();
        for (String colour : about.colors()) {
            has.add(colour.trim().toUpperCase(Locale.ROOT));
        }
        for (String colour : wanted) {
            if (colour.equals("C")) {
                if (!has.isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!has.contains(colour)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every word typed appears somewhere in the card.
     *
     * <p>Word by word rather than as one string, because "bolt light" and "light bolt" are the
     * same question and somebody typing half-remembered names types them in any order.
     */
    private static boolean saysAll(CardMetadata about, String text) {
        if (text.isEmpty()) {
            return true;
        }
        String haystack = lower(about.name()) + " " + lower(about.typeLine()) + " "
                + lower(about.setName()) + " " + lower(about.setCode()) + " "
                + lower(about.oracleText());
        for (String word : text.split("\\s+")) {
            if (!word.isEmpty() && !haystack.contains(word)) {
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
            case COLOUR -> Comparator.comparing(CollectionSearch::colourKey);
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
     * A card's colours as one sortable string, in Magic's own order.
     *
     * <p>WUBRG rather than alphabetical, because that is the order every player already reads
     * colours in and a list that puts blue after black looks broken to them.
     */
    private static String colourKey(Row row) {
        if (!row.isKnown()) {
            return "~";
        }
        Set<String> has = new LinkedHashSet<>();
        for (String colour : row.about().colors()) {
            has.add(colour.trim().toUpperCase(Locale.ROOT));
        }
        if (has.isEmpty()) {
            // Colourless after the colours rather than before them: it is where a player
            // looks for artifacts and lands, which is the end of the binder.
            return "z";
        }
        StringBuilder key = new StringBuilder();
        // How many colours first, so the mono-coloured cards are together and the gold ones
        // follow, which is how anybody lays a collection out.
        key.append((char) ('0' + Math.min(9, has.size())));
        // The colour where a card has it, and a character that sorts after every letter
        // where it does not - so a white card comes before a blue one because W beats the
        // placeholder in the first position, which is what WUBRG order actually is.
        for (char colour : new char[] {'W', 'U', 'B', 'R', 'G'}) {
            key.append(has.contains(String.valueOf(colour)) ? colour : '~');
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

    private static Set<String> upperCased(Set<String> colours) {
        Set<String> kept = new LinkedHashSet<>();
        for (String colour : colours) {
            if (colour != null && !colour.isBlank()) {
                kept.add(colour.trim().toUpperCase(Locale.ROOT));
            }
        }
        return java.util.Collections.unmodifiableSet(kept);
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
