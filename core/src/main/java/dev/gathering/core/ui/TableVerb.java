package dev.gathering.core.ui;

import java.util.Locale;

/**
 * The verbs a mat carries a button for.
 * <p>The handful a player uses every single turn, and only those. Everything else a table can
 * do is on a menu or a key, because a mat with twenty buttons on it is a control panel and a
 * player looking for the one they want reads all twenty every time.
 * <p>One list, shared by the seated board and the board drawn on the block, so the two cannot
 * offer different buttons in different places - and by the layout, so a button can never be
 * drawn somewhere nothing listens for a click.
 */
public enum TableVerb {

    /** Straightens everything you have. The first thing anybody does on their turn. */
    UNTAP,

    /** One card. The second thing anybody does on their turn. */
    DRAW,

    /** Your library. Constant, and easy to forget, which is why it is worth a button. */
    SHUFFLE,

    /** A new opening hand, one smaller. Only ever wanted at the start, but wanted badly. */
    MULLIGAN;

    private final String key = "verb.gathering." + name().toLowerCase(Locale.ROOT);

    /**
     * The key its name is written under.
     * <p>Built once. Every button on every mat asks for this every frame, and lowercasing a
     * constant sixty times a second is sixty allocations to arrive at the same string.
     */
    public String key() {
        return key;
    }

    /** How many buttons a mat carries. */
    public static int count() {
        return values().length;
    }
}
