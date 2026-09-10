package dev.gathering.core.card;

import java.util.Locale;
import java.util.Optional;

/**
 * The five colors, and what each of them is about.
 * <p>In WUBRG order, which is the order Magic writes them and the order they sit in on the
 * back of every card - white at the top, then blue, black, red and green clockwise. The wheel
 * is the one diagram in the game a complete newcomer has already looked at hundreds of times
 * without being told what it was.
 * <p>Each one carries a translation key for its name and one for the five words that say what
 * it stands for. Those five words are the shortest true answer to "what is Magic about", and
 * they are the thing a control tutorial deliberately does not teach - so the screen where
 * somebody picks a color is exactly where they belong.
 * <p>Pure. It knows nothing about mana symbols, cards or sets: it is five names, five letters
 * and five sentences.
 */
public enum MagicColor {

    /** Peace, law, structure, selflessness, equality. */
    WHITE('W'),

    /** Knowledge, deceit, caution, deliberation, perfection. */
    BLUE('U'),

    /** Power, self-interest, death, sacrifice, uninhibitedness. */
    BLACK('B'),

    /** Freedom, emotion, action, impulse, destruction. */
    RED('R'),

    /** Nature, wildlife, connection, spirituality, tradition. */
    GREEN('G');

    private final char letter;
    private final String key;

    MagicColor(char letter) {
        this.letter = letter;
        this.key = "color.gathering." + name().toLowerCase(Locale.ROOT);
    }

    /** The letter Magic writes it as: one of WUBRG. */
    public char letter() {
        return letter;
    }

    /** The letter as a string, which is what a data component and a mana symbol both want. */
    public String code() {
        return String.valueOf(letter);
    }

    /** The key its name is written under. */
    public String key() {
        return key;
    }

    /** The key its five words are written under. */
    public String philosophyKey() {
        return key + ".philosophy";
    }

    /**
     * The color with that letter, or empty.
     * <p>Takes either case, because this reads letters that have been through a data
     * component, a packet and a config file, and being strict about the case of a single
     * letter would only ever refuse somebody who meant the right thing.
     */
    public static Optional<MagicColor> of(char letter) {
        char wanted = Character.toUpperCase(letter);
        for (MagicColor color : values()) {
            if (color.letter == wanted) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    /** The same, for a string that should be exactly one letter. */
    public static Optional<MagicColor> of(String letter) {
        return letter == null || letter.length() != 1
                ? Optional.empty()
                : of(letter.charAt(0));
    }

    /** How many there are, which is why the wheel is a pentagon. */
    public static int count() {
        return values().length;
    }
}
