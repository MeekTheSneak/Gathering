package dev.gathering.core.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How to tell two tokens with the same name apart, in the words a player would use.
 * <p>"Cat" is a dozen different tokens: a 1/1 white Cat, a 2/2 white Cat, a 1/1 white Cat with
 * lifelink, a 2/2 green one. Asked for by name, a table has to ask which - and a list of four
 * rows that all say "Cat" is not an answer anybody can pick from. What separates them is what
 * matters when it is on the table: its size, its colors, and what it does.
 * <p>Pure, and built from what a client is already told about a card, so the label a chooser
 * shows is made from the same facts the card itself is drawn from.
 */
public final class TokenVariant {

    /** Long enough for "Flying, first strike, lifelink" and short enough for one row. */
    public static final int LONGEST_ABILITIES = 48;

    private TokenVariant() {
    }

    /**
     * What sets this token apart: size, then colors, then the first thing its rules text says.
     * <p>For example {@code 2/2 green}, {@code 1/1 white · Lifelink}, {@code colorless · Sacrifice
     * this token: Add one mana of any color.} - each part only if the token has it.
     *
     * @param strength   printed power and toughness as one string, or empty
     * @param colors     the token's color letters
     * @param oracleText its rules text, or empty
     */
    public static String describe(String strength, Set<String> colors, String oracleText) {
        List<String> parts = new ArrayList<>();
        String size = strength == null ? "" : strength.strip();
        String color = colorWords(colors);
        String sizeAndColor = (size + " " + color).strip();
        if (!sizeAndColor.isEmpty()) {
            parts.add(sizeAndColor);
        }
        String does = firstRule(oracleText);
        if (!does.isEmpty()) {
            parts.add(does);
        }
        return String.join(" · ", parts);
    }

    /** The colors in the order Magic prints them, as words: "white and blue", "colorless". */
    static String colorWords(Set<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return "colorless";
        }
        List<String> words = new ArrayList<>();
        for (String letter : List.of("W", "U", "B", "R", "G")) {
            if (colors.contains(letter)) {
                words.add(switch (letter) {
                    case "W" -> "white";
                    case "U" -> "blue";
                    case "B" -> "black";
                    case "R" -> "red";
                    default -> "green";
                });
            }
        }
        if (words.isEmpty()) {
            return "colorless";
        }
        if (words.size() == 1) {
            return words.getFirst();
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.getLast();
    }

    /**
     * The first line of the rules text, trimmed to fit a row.
     * <p>The first line because that is where keywords are printed, and keywords are what
     * separate most tokens of one name. Reminder text in parentheses is dropped: it explains
     * a keyword the row has already named.
     */
    static String firstRule(String oracleText) {
        if (oracleText == null || oracleText.isBlank()) {
            return "";
        }
        String line = oracleText.strip().split("\\R", 2)[0];
        line = line.replaceAll("\\s*\\([^)]*\\)", "").strip();
        if (line.length() > LONGEST_ABILITIES) {
            line = line.substring(0, LONGEST_ABILITIES - 3).strip() + "...";
        }
        return line.isEmpty() ? "" : line.substring(0, 1).toUpperCase(Locale.ROOT) + line.substring(1);
    }
}
