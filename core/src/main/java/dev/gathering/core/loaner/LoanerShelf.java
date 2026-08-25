package dev.gathering.core.loaner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The loaner decks a server is lending out.
 *
 * <p>An admin drops decklists in a directory and every table on the server can lend them.
 * The point is the first minute: somebody who has just joined, owns nothing and may not be
 * allowed to import anything can still sit down and play a real deck, which is the whole
 * starter-deck moment the brief asks for.
 *
 * <p>What is here is the rule about the shelf and nothing about files: which of them are
 * worth offering, what each is called, and what happens when two of them want the same name.
 * Reading a directory is the server's job; deciding what a directory means is this one's, and
 * it is the part with the edge cases in it.
 */
public record LoanerShelf(List<Loaner> decks) {

    /**
     * How many decks a shelf may hold.
     *
     * <p>The list crosses the wire and goes on a screen somebody has to read. A server with
     * two hundred loaners has a menu nobody can choose from, which is not lending.
     */
    public static final int MOST = 32;

    /** As long as a loaner's name may be, which is what a button can hold. */
    public static final int MOST_NAME_CHARACTERS = 48;

    /** One deck on the shelf: what it is called, and the list it is. */
    public record Loaner(String name, String decklist) {

        public Loaner {
            name = name == null ? "" : name.strip();
            decklist = decklist == null ? "" : decklist;
        }
    }

    public static final LoanerShelf EMPTY = new LoanerShelf(List.of());

    public LoanerShelf {
        decks = decks == null ? List.of() : List.copyOf(decks);
    }

    public boolean isEmpty() {
        return decks.isEmpty();
    }

    public int size() {
        return decks.size();
    }

    public List<String> names() {
        return decks.stream().map(Loaner::name).toList();
    }

    /** The deck by that name, ignoring case as the shelf itself does. */
    public Optional<Loaner> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (Loaner deck : decks) {
            if (deck.name().equalsIgnoreCase(name)) {
                return Optional.of(deck);
            }
        }
        return Optional.empty();
    }

    /**
     * A shelf out of what a directory held: file name to contents.
     *
     * <p>Blank lists are dropped rather than offered, because a loaner that turns out to be
     * nothing is a player sitting down to an empty table with no idea why. Names are taken
     * from the file, tidied, and made unique, so two files that tidy to the same name are two
     * decks somebody can still tell apart rather than one deck that shadowed the other.
     *
     * <p>Ordered by name so the menu is the same on every client and after every restart: a
     * list whose order came from a directory listing is a list where the deck you picked last
     * time has moved.
     */
    public static LoanerShelf of(Map<String, String> files) {
        if (files == null || files.isEmpty()) {
            return EMPTY;
        }
        List<Map.Entry<String, String>> sorted = new ArrayList<>(files.entrySet());
        sorted.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));

        // Case-insensitive, and iterating in that order. The server keeps the shelf the same
        // way, so "which decks, in what order" is stated once: two lists that agreed on
        // ordering by both happening to sort are two lists that stop agreeing.
        Map<String, Loaner> byName = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> file : sorted) {
            if (file.getValue() == null || file.getValue().isBlank()) {
                continue;
            }
            String name = nameOf(file.getKey());
            if (name.isEmpty()) {
                continue;
            }
            byName.put(unique(byName, name), new Loaner(name, file.getValue()));
            if (byName.size() >= MOST) {
                break;
            }
        }
        // Keyed on the unique name while it was being built, but each deck carries the name
        // it will be shown under, which is the one the client sends back.
        List<Loaner> decks = new ArrayList<>();
        for (Map.Entry<String, Loaner> entry : byName.entrySet()) {
            decks.add(new Loaner(entry.getKey(), entry.getValue().decklist()));
        }
        return new LoanerShelf(decks);
    }

    /**
     * What a file is called, as something to put on a button.
     *
     * <p>{@code mono-red_burn.txt} is "Mono Red Burn". A name is what an admin typed when
     * they saved the file, so this only does what a file name cannot carry: drops the
     * extension, turns the separators back into spaces, and capitalises.
     */
    public static String nameOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String bare = fileName.strip();
        int dot = bare.lastIndexOf('.');
        if (dot == 0) {
            // A hidden file: all extension and no name. ".txt" is not a deck called ".txt".
            return "";
        }
        if (dot > 0) {
            bare = bare.substring(0, dot);
        }
        bare = bare.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").strip();
        if (bare.isEmpty()) {
            return "";
        }
        StringBuilder tidied = new StringBuilder(bare.length());
        boolean startOfWord = true;
        for (char letter : bare.toCharArray()) {
            if (letter == ' ') {
                startOfWord = true;
                tidied.append(letter);
                continue;
            }
            tidied.append(startOfWord
                    ? Character.toUpperCase(letter)
                    : Character.toLowerCase(letter));
            startOfWord = false;
        }
        String name = tidied.toString();
        return name.length() <= MOST_NAME_CHARACTERS
                ? name
                : name.substring(0, MOST_NAME_CHARACTERS).strip();
    }

    /** That name, or that name with a number, so nothing on the shelf is hidden. */
    private static String unique(Map<String, Loaner> taken, String name) {
        if (!taken.containsKey(name)) {
            return name;
        }
        for (int suffix = 2; suffix <= MOST + 1; suffix++) {
            String tried = shorten(name, " " + suffix) + " " + suffix;
            if (!taken.containsKey(tried)) {
                return tried;
            }
        }
        return name + " " + Long.toHexString(name.toLowerCase(Locale.ROOT).hashCode());
    }

    /** Room made for a suffix, so a name at the cap does not grow past it. */
    private static String shorten(String name, String suffix) {
        int room = MOST_NAME_CHARACTERS - suffix.length();
        return name.length() <= room ? name : name.substring(0, room).strip();
    }
}
