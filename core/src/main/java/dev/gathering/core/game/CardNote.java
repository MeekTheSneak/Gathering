package dev.gathering.core.game;

/**
 * What somebody has written on a card.
 *
 * <p>The thing a player reaches for a pen for at a real table: "flying until end of turn",
 * "this is the morph", "goes to their side next upkeep". The mod has no rules engine and
 * never will, so the way a table remembers a rule is by writing it down - and until now the
 * only way to do that was to say it out loud and hope.
 *
 * <p>Player-written text that every other player is shown, so it is cleaned rather than
 * trusted. One line, no formatting codes, and short enough to fit across a card: a note is a
 * reminder, and a paragraph on a card is a paragraph covering a card.
 */
public final class CardNote {

    /**
     * How long a note may be.
     *
     * <p>About what fits across a card at the size a two-player board draws one, twice over -
     * so a note is written small rather than trimmed, and a player who wants to write an
     * essay is being asked to say it in the chat instead.
     */
    public static final int LONGEST = 48;

    /** Minecraft's formatting escape. Player text carrying one would color the board. */
    private static final char FORMATTING = '§';

    private CardNote() {
    }

    /**
     * The note as it will be stored, or null for no note at all.
     *
     * <p>One line: newlines and tabs become spaces and runs of space collapse, because a note
     * is drawn on a band across a card and a second line has nowhere to go. Control characters
     * and the formatting escape are dropped outright rather than replaced - they are not
     * writing, and a card whose note recolors the board is a card somebody used as a paint
     * brush.
     */
    public static String clean(String written) {
        if (written == null) {
            return null;
        }
        StringBuilder tidy = new StringBuilder(Math.min(written.length(), LONGEST));
        boolean space = false;
        for (int index = 0; index < written.length() && tidy.length() < LONGEST; index++) {
            char letter = written.charAt(index);
            if (letter == FORMATTING) {
                continue;
            }
            if (Character.isWhitespace(letter)) {
                space = tidy.length() > 0;
                continue;
            }
            if (Character.isISOControl(letter)) {
                continue;
            }
            if (space) {
                // Room for the space and the word after it, or neither. Writing the space
                // first and then finding the note full left it ending on one, which is a
                // note that comes back different the second time it is cleaned.
                if (tidy.length() + 1 >= LONGEST) {
                    break;
                }
                tidy.append(' ');
                space = false;
            }
            tidy.append(letter);
        }
        return tidy.isEmpty() ? null : tidy.toString();
    }

    /** Whether these two notes are the same note, treating null and blank as the same. */
    public static boolean same(String one, String other) {
        return java.util.Objects.equals(clean(one), clean(other));
    }
}
