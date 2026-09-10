package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Putting a typed query against a list of verbs and getting them back in a useful order.
 * <p>Pure, and separate from {@link TableActions} on purpose: the catalogue knows what a verb
 * is called in the source, and the only text worth searching is what it is called in the
 * player's own language, which lives in the language file and reaches this as a string. So the
 * caller hands over rows that already carry their translated name and this decides the order.
 * <p>The order is the whole feature. A search box that returns the right sixteen rows in the
 * wrong order is a search box somebody scrolls, and scrolling a search result is slower than
 * the menu they came from.
 */
public final class ActionSearch {

    private ActionSearch() {
    }

    /**
     * One verb as it reads to this player.
     *
     * @param id      the catalogue id, which is what the caller does something with
     * @param label   its name in the player's language, and the primary text searched
     * @param aliases every other word it may be found by, already gathered from the catalogue
     *                and from whatever the translation adds
     */
    public record Row(String id, String label, List<String> aliases) {

        public Row {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    /**
     * The rows that match, best first.
     * <p>An empty query keeps every row in the order it came in, because the order it came in
     * is the catalogue's, and the catalogue is ordered by how often a verb is wanted. A query
     * that matches nothing gives nothing rather than everything: a list that ignores what was
     * typed reads as the box being broken.
     * <p>Ties keep their original order. That matters more than it sounds: "to graveyard",
     * "to exile" and "to hand" all score the same against "to", and a stable sort means they
     * come out in the order the menus list them rather than in whatever order a sort happened
     * to leave them, which would differ between two players typing the same letters.
     */
    public static List<Row> matching(String query, List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (query == null || query.strip().isEmpty()) {
            return List.copyOf(rows);
        }
        List<Row> found = new ArrayList<>();
        for (Row row : rows) {
            if (TableActions.rank(query, row.label(), row.aliases()) > 0) {
                found.add(row);
            }
        }
        // Stable, so equal scores keep catalogue order. Sorting descending by score with a
        // stable sort is the whole of it - no tiebreak field is needed and adding one would
        // only be a second opinion about an order the catalogue has already given.
        found.sort(Comparator.comparingInt(
                (Row row) -> TableActions.rank(query, row.label(), row.aliases())).reversed());
        return List.copyOf(found);
    }
}
