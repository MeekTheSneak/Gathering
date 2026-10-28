package dev.gathering.core.deck;

import dev.gathering.core.decklist.DecklistEntry;

/**
 * A line that parsed cleanly but matched no card.
 * <p>Kept whole, with its original text and line number, because the only useful thing to
 * do with an unresolved line is show it to the person who pasted it.
 */
public record UnresolvedEntry(DecklistEntry entry, String reason) {

    @Override
    public String toString() {
        return "line " + entry.lineNumber() + ": " + reason + " (" + entry.sourceLine().strip() + ")";
    }
}
