package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The handful of things somebody keeps asking for, kept so they do not have to type them again.
 * <p>Making a token means typing its name, and a deck that makes Treasures makes a hundred of
 * them in an evening. The same is true of counters: a table playing a set with a keyword
 * counter types that word every turn. Neither is worth a screen of its own, and both are worth
 * a row of what you used last.
 * <p>Three properties this has to have, and each of them is a rule somebody could reasonably
 * get wrong:
 * <ul>
 *   <li><b>Names, never identities.</b> What is kept is the word a player typed - "Treasure",
 *       "stun" - and never the id of a card that happened to make one. An instance id is a
 *       handle on one particular card in one particular game; writing it into a settings file
 *       would keep a pointer to something that stopped existing when the game ended, and would
 *       make a file on disk into a thing that says what was in somebody's game.</li>
 *   <li><b>Bounded.</b> A list that grows every time it is used is a settings file that grows
 *       forever, and a row of shortcuts nobody can find anything in.</li>
 *   <li><b>Scoped.</b> Servers do not agree about what tokens exist. A name remembered on one
 *       is not an offer worth making on another, so each is kept under its own key.</li>
 * </ul>
 * <p>Pure. It knows nothing about cards, servers or files; it keeps a short list of strings in
 * the order they were last used, and packs it into one line.
 */
public final class Recents {

    /**
     * How many are kept.
     * <p>Enough to cover a deck's repertoire - a Treasure, a Clue, a Blood, a 1/1 flier, a
     * Food - and few enough to fit on one row without becoming a list to read. Past this, the
     * one used longest ago makes room, which is the only rule that needs no explaining.
     */
    public static final int MOST_KEPT = 8;

    /**
     * The longest name kept.
     * <p>Card and token names are far shorter than this; a counter name is whatever somebody
     * typed. A settings file is not the place to find out how long that can get.
     */
    public static final int LONGEST_NAME = 64;

    /**
     * What separates one name from the next on the line, and what escapes it.
     * <p>A pipe, because a settings file is meant to be readable and a control character is
     * not. Names are player-typed and may contain one, so it is escaped rather than banned -
     * banning it would silently drop the name of the one token somebody wanted.
     */
    private static final char BETWEEN = '|';
    private static final char ESCAPE = '\\';

    private Recents() {
    }

    /**
     * Puts a name at the front, moving it rather than adding it twice.
     * <p>Most recent first, so the row reads in the order somebody would guess. Using one
     * again is the commonest thing that happens to this list, and it must not grow it.
     *
     * @return a new list; the one passed in is not touched
     */
    public static List<String> remember(List<String> had, String used) {
        String name = tidy(used);
        if (name.isEmpty()) {
            return had == null ? List.of() : List.copyOf(had);
        }
        List<String> next = new ArrayList<>();
        next.add(name);
        if (had != null) {
            for (String older : had) {
                String kept = tidy(older);
                // Compared without case, because "treasure" and "Treasure" are one token and
                // two rows of them is the list failing at its one job.
                if (!kept.isEmpty() && !kept.equalsIgnoreCase(name)) {
                    next.add(kept);
                }
            }
        }
        return List.copyOf(next.subList(0, Math.min(next.size(), MOST_KEPT)));
    }

    /** Drops one, for a name that turned out to be a typo or a token this server has not got. */
    public static List<String> forget(List<String> had, String name) {
        String going = tidy(name);
        if (had == null || going.isEmpty()) {
            return had == null ? List.of() : List.copyOf(had);
        }
        List<String> next = new ArrayList<>();
        for (String kept : had) {
            if (!tidy(kept).equalsIgnoreCase(going)) {
                next.add(kept);
            }
        }
        return List.copyOf(next);
    }

    /** Trimmed, bounded, and with the line breaks a settings file cannot carry taken out. */
    private static String tidy(String name) {
        if (name == null) {
            return "";
        }
        String clean = name.replace('\n', ' ').replace('\r', ' ').strip();
        return clean.length() <= LONGEST_NAME ? clean : clean.substring(0, LONGEST_NAME).strip();
    }

    /**
     * The list as one line, for a settings file.
     * <p>Escaped rather than banned: a counter somebody named "Stun|Shield" is still a counter,
     * and a pack that quietly lost half of it would be worse than one that is a little uglier
     * to read.
     */
    public static String pack(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder();
        for (String name : names) {
            String kept = tidy(name);
            if (kept.isEmpty()) {
                continue;
            }
            if (line.length() > 0) {
                line.append(BETWEEN);
            }
            for (int at = 0; at < kept.length(); at++) {
                char letter = kept.charAt(at);
                if (letter == BETWEEN || letter == ESCAPE) {
                    line.append(ESCAPE);
                }
                line.append(letter);
            }
        }
        return line.toString();
    }

    /**
     * The line back into a list, bounded on the way in.
     * <p>Bounded here as well as in {@link #remember}, because a settings file is a thing
     * somebody can edit: a hand-written line with four hundred names in it must not become
     * four hundred names in memory and a row four hundred wide.
     */
    public static List<String> unpack(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        StringBuilder name = new StringBuilder();
        boolean escaped = false;
        for (int at = 0; at < line.length(); at++) {
            char letter = line.charAt(at);
            if (escaped) {
                name.append(letter);
                escaped = false;
            } else if (letter == ESCAPE) {
                escaped = true;
            } else if (letter == BETWEEN) {
                addTo(found, name.toString());
                name.setLength(0);
            } else {
                name.append(letter);
            }
        }
        addTo(found, name.toString());
        return List.copyOf(found.subList(0, Math.min(found.size(), MOST_KEPT)));
    }

    private static void addTo(List<String> found, String name) {
        String kept = tidy(name);
        if (kept.isEmpty()) {
            return;
        }
        for (String already : found) {
            if (already.equalsIgnoreCase(kept)) {
                return;
            }
        }
        found.add(kept);
    }

    /**
     * A settings key fragment for one server, safe to write and stable across restarts.
     * <p>Servers do not agree about what tokens exist, so a name remembered on one is not an
     * offer worth making on another. The identity a caller has - an address, a save folder -
     * is none of this class's business and is whatever the caller can honestly supply; what
     * this does is make it into something a config key can hold.
     * <p>The readable part is kept at the front so somebody looking at the file can tell which
     * server a line belongs to, and a hash of the whole thing is appended so two that differ
     * only in what was stripped do not collide.
     */
    public static String scopeKey(String server) {
        String identity = server == null ? "" : server.strip();
        if (identity.isEmpty()) {
            return "unknown";
        }
        StringBuilder readable = new StringBuilder();
        for (int at = 0; at < identity.length() && readable.length() < 24; at++) {
            char letter = Character.toLowerCase(identity.charAt(at));
            if (letter >= 'a' && letter <= 'z' || letter >= '0' && letter <= '9') {
                readable.append(letter);
            } else if (readable.length() > 0 && readable.charAt(readable.length() - 1) != '_') {
                readable.append('_');
            }
        }
        // Not a security property and not pretending to be one: this only has to tell two
        // servers apart, and it has to give the same answer next time the game starts.
        String stamp = Integer.toHexString(identity.hashCode()).toLowerCase(Locale.ROOT);
        String head = readable.toString();
        while (head.endsWith("_")) {
            head = head.substring(0, head.length() - 1);
        }
        return (head.isEmpty() ? "s" : head) + "_" + stamp;
    }
}
