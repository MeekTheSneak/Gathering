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

    private CardNote() {
    }

    /**
     * The note as it will be stored, or null for no note at all.
     *
     * <p>One line and nothing that could recolor a board - the same rule table chat follows,
     * written once in {@link PlayerText} because they are the same problem: a line of typing
     * from one client, drawn on everybody else's screen.
     */
    public static String clean(String written) {
        return PlayerText.oneLine(written, LONGEST);
    }

    /** Whether these two notes are the same note, treating null and blank as the same. */
    public static boolean same(String one, String other) {
        return java.util.Objects.equals(clean(one), clean(other));
    }
}
