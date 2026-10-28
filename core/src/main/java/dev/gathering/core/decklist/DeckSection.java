package dev.gathering.core.decklist;

import java.util.Locale;
import java.util.Optional;

/** Which pile of a decklist an entry belongs to. */
public enum DeckSection {
    COMMANDER,
    COMPANION,
    MAINBOARD,
    SIDEBOARD,
    MAYBEBOARD,
    TOKENS;

    /**
     * Recognizes the section headers the common exporters write. Returns empty for anything
     * that is not a header, which is how the parser tells "Deck" the header from a card line.
     */
    public static Optional<DeckSection> fromHeader(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).trim();
        return Optional.ofNullable(switch (key) {
            case "commander", "commanders", "commander(s)" -> COMMANDER;
            case "companion" -> COMPANION;
            case "deck", "main", "mainboard", "maindeck", "main deck", "creatures" -> MAINBOARD;
            case "sideboard", "side", "side board", "sb" -> SIDEBOARD;
            case "maybeboard", "maybe", "maybe board", "considering" -> MAYBEBOARD;
            case "tokens", "token" -> TOKENS;
            default -> null;
        });
    }

}
