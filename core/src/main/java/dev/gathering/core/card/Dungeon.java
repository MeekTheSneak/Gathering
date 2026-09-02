package dev.gathering.core.card;

/**
 * The dungeons, which are the four of them.
 * <p>A dungeon is a real printed card that never goes in a deck: it starts outside the game
 * and comes in when something ventures. There is no way to draw one, buy one, or open one in a
 * pack, so a table that could not bring one in was a table where half of Adventures in the
 * Forgotten Realms did nothing - and the ones that exist are a closed list, because Wizards
 * has printed four of them and a fifth would be news.
 * <p>Named rather than searched. A client that could ask for "any card whose type is dungeon"
 * would be a client naming a card the server then fetches, and every name that crosses that
 * boundary in this mod is an enum for exactly that reason. These four are a fact about Magic,
 * so they are written down here and the client sends which one.
 * <p>Pure.
 */
public enum Dungeon {

    /** The starter one, and the one most decks that venture at all are venturing into. */
    LOST_MINE_OF_PHANDELVER("Lost Mine of Phandelver"),

    /** The long one. */
    DUNGEON_OF_THE_MAD_MAGE("Dungeon of the Mad Mage"),

    /** The one that kills you. */
    TOMB_OF_ANNIHILATION("Tomb of Annihilation"),

    /** Baldur's Gate's, which is where the initiative goes. */
    UNDERCITY("Undercity");

    private final String cardName;

    Dungeon(String cardName) {
        this.cardName = cardName;
    }

    /** Exactly as Scryfall spells it, because that is what the lookup is given. */
    public String cardName() {
        return cardName;
    }

    /** The four, once, so a lookup off the wire does not copy an array to answer. */
    private static final Dungeon[] ALL = values();

    /** The one at that position, clamped, for an index that arrived off a wire. */
    public static Dungeon at(int index) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, index))];
    }
}
