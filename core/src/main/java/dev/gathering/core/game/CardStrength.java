package dev.gathering.core.game;

/**
 * The power and toughness somebody has written over the printed ones.
 * <p>What a player reaches for when the card in front of them is no longer the card that was
 * printed: a 2/2 that is a 6/6 until end of turn, a creature that is a copy of something else,
 * an animated land. At a real table this is said out loud and forgotten twice a turn; here it
 * is written in the corner where the numbers already are, and everyone can see it.
 * <p><b>Typed, not calculated.</b> You write "6/6" and the card says 6/6. The mod does not add
 * the counters up, does not know what the printed numbers are, and does not care whether what
 * you typed is right - section 16, no rules enforcement, and this is the sharpest case of it.
 * A mod that worked the numbers out would be wrong about half of them and would have to be
 * argued with; a mod that writes down what you tell it is never wrong, because it is you
 * saying it.
 * <p>Player-written text every other player is shown, so it is cleaned rather than trusted.
 * Short, one line, and only the characters power and toughness are made of.
 */
public final class CardStrength {

    /**
     * How long an override may be.
     * <p>"10/10" is five, and a power printed as a star with something added to it is about
     * the same, and the box it is drawn in is the corner of a card.
     * Eight leaves room for the longest thing anybody honestly writes there and stops the
     * corner of a card being used as a second note.
     */
    public static final int LONGEST = 8;

    /**
     * What power and toughness are made of.
     * <p>Digits, a slash, plus and minus, and the star that a card whose power is a count of
     * something prints. A letter would be a note, and notes have their own place on the card.
     */
    private static boolean allowed(char letter) {
        return Character.isDigit(letter)
                || letter == '/'
                || letter == '+'
                || letter == '-'
                || letter == '*';
    }

    private CardStrength() {
    }

    /**
     * The override as it will be stored, or null for a card showing its printed numbers.
     * <p>Anything that is not part of a power and toughness is dropped rather than rejected:
     * somebody typing "6 / 6" means 6/6, and refusing it would be a screen arguing with a
     * player about spaces. What comes back is either something worth drawing or nothing.
     */
    public static String clean(String written) {
        if (written == null) {
            return null;
        }
        StringBuilder tidy = new StringBuilder(Math.min(written.length(), LONGEST));
        for (int index = 0; index < written.length() && tidy.length() < LONGEST; index++) {
            char letter = written.charAt(index);
            if (allowed(letter)) {
                tidy.append(letter);
            }
        }
        return tidy.isEmpty() ? null : tidy.toString();
    }

    /** Whether these two overrides are the same, treating null and nothing as the same. */
    public static boolean same(String one, String other) {
        return java.util.Objects.equals(clean(one), clean(other));
    }
}
