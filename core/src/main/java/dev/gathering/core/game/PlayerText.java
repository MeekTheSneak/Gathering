package dev.gathering.core.game;

/**
 * Cleaning up something a player typed that everybody else is going to read.
 *
 * <p>Two places in the mod take a line of ordinary writing from one player and show it to the
 * rest of the table: the pen that writes on a card, and the chat that talks to the table. They
 * are the same problem - text from a client, drawn on somebody else's screen - and the rule
 * that makes them safe is the same rule, so it is written once here rather than twice with a
 * chance of drifting.
 *
 * <p>One line, no formatting codes, and no longer than the space it is drawn in. Newlines and
 * tabs become spaces and runs of space collapse; control characters and Minecraft's formatting
 * escape are dropped outright rather than replaced, because they are not writing - a note that
 * recolors the board is a note somebody used as a paint brush.
 *
 * <p>Pure.
 */
public final class PlayerText {

    /** Minecraft's formatting escape. Player text carrying one would color the board. */
    private static final char FORMATTING = '§';

    private PlayerText() {
    }

    /**
     * The line as it will be stored and shown, or null for nothing worth showing.
     *
     * <p>Null rather than an empty string, so "they typed nothing" and "they typed spaces" are
     * the same answer everywhere - and so a caller can tell whether there is anything to draw
     * without having to remember to trim first.
     */
    public static String oneLine(String written, int longest) {
        if (written == null || longest <= 0) {
            return null;
        }
        StringBuilder tidy = new StringBuilder(Math.min(written.length(), longest));
        boolean space = false;
        for (int index = 0; index < written.length() && tidy.length() < longest; index++) {
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
                // first and then finding the line full left it ending on one, which is a
                // line that comes back different the second time it is cleaned.
                if (tidy.length() + 1 >= longest) {
                    break;
                }
                tidy.append(' ');
                space = false;
            }
            tidy.append(letter);
        }
        return tidy.isEmpty() ? null : tidy.toString();
    }
}
