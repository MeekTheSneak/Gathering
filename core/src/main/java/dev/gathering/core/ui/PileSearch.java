package dev.gathering.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What somebody typed into the box over a pile they are searching, asked of one card.
 * <p>Searching a library for a card is reading sixty names for one of them, and the owner asked to
 * be able to type instead. Every word typed has to be somewhere in what the card says - its name,
 * its type line, its rules text, either face - in any order and any case, so "elf warrior" finds a
 * creature called an Elf Warrior and "draw" finds everything that draws.
 * <p>Plain words rather than the collection's search language: the question at a table is "where
 * is it", asked in the middle of a turn, and the card is already known to be one of these.
 * <p>Pure.
 */
public final class PileSearch {

    /** The longest search a box over a pile takes. */
    public static final int MOST_CHARACTERS = 50;

    private PileSearch() {
    }

    /** The words of a search, lowercased; none for a blank one. */
    public static List<String> words(String typed) {
        List<String> words = new ArrayList<>();
        if (typed == null) {
            return words;
        }
        for (String word : typed.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Whether a card saying these things is one the search asks for. Everything matches a blank
     * search, and a card with nothing known about it matches no other.
     */
    public static boolean matches(List<String> words, List<String> said) {
        if (words.isEmpty()) {
            return true;
        }
        StringBuilder all = new StringBuilder();
        for (String part : said) {
            if (part != null) {
                all.append(part.toLowerCase(Locale.ROOT)).append('\n');
            }
        }
        String text = all.toString();
        for (String word : words) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
