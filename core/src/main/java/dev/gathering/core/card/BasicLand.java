package dev.gathering.core.card;

import java.util.Locale;

/**
 * The basic lands, once.
 *
 * <p>Two payloads need this list - adding basics to a deck being built, where they are free
 * because deck construction says so, and fetching one out of a library mid-game - and each
 * briefly kept its own copy. Two lists of the same six cards is one Wastes away from
 * disagreeing, which is exactly what happened: the deck-building list had five, so a
 * colourless deck could not be given its own lands.
 *
 * <p>Wastes is here because it is a basic land, prints as one, and is fetched and added by
 * the same effects. A list that is right about Magic until somebody plays colourless is not
 * right about Magic.
 */
public enum BasicLand {
    PLAINS("Plains", "{W}"),
    ISLAND("Island", "{U}"),
    SWAMP("Swamp", "{B}"),
    MOUNTAIN("Mountain", "{R}"),
    FOREST("Forest", "{G}"),
    WASTES("Wastes", "{C}");

    private final String printedName;
    private final String symbol;

    BasicLand(String printedName, String symbol) {
        this.printedName = printedName;
        this.symbol = symbol;
    }

    /** Exactly as it is printed, which is also what a card lookup is given. */
    public String printedName() {
        return printedName;
    }

    /**
     * The mana symbol its button is marked with.
     *
     * <p>Rather than the card's name. Six names do not fit across a panel that is a fraction
     * of the window - they came out as "ain", "lar", "mp", which is a row of buttons nobody
     * can tell apart. A mana symbol is one glyph, it is what every player of this game
     * already reads colours as, and the name is on the tooltip for anybody who wants it
     * spelled out.
     */
    public String symbol() {
        return symbol;
    }

    /** The key the tooltip names it from. */
    public String translationKey() {
        return "screen.gathering.deck.basic." + name().toLowerCase(Locale.ROOT);
    }
}
