package dev.gathering.core.decklist;

import java.util.Optional;

/**
 * One line of a decklist, parsed but not yet resolved to a printing.
 *
 * <p>The set code and collector number are hints, not identity. Resolution happens later
 * against the Scryfall cache, where a missing or wrong hint falls back to the cheapest
 * matching printing and an ambiguity is offered to the importer rather than guessed at.
 *
 * <p>{@code scryfallId} is the exception: when a source knows the exact printing - a deck
 * site's API hands them over, where a text export never can - resolution is by id and there
 * is nothing left to guess. Empty for anything that came from pasted text.
 */
public record DecklistEntry(
        int quantity,
        String name,
        String setCode,
        String collectorNumber,
        boolean foil,
        DeckSection section,
        int lineNumber,
        String sourceLine,
        java.util.UUID scryfallId) {

    /** For sources that only know a name - which is every text decklist. */
    public DecklistEntry(
            int quantity,
            String name,
            String setCode,
            String collectorNumber,
            boolean foil,
            DeckSection section,
            int lineNumber,
            String sourceLine) {
        this(quantity, name, setCode, collectorNumber, foil, section, lineNumber, sourceLine, null);
    }

    /** The exact printing, when the source knew it. */
    public Optional<java.util.UUID> printing() {
        return Optional.ofNullable(scryfallId);
    }

    public Optional<String> set() {
        return Optional.ofNullable(setCode);
    }

    /** Whether this line named a specific printing rather than just a card. */
    public boolean hasPrintingHint() {
        return setCode != null;
    }

    public DecklistEntry withSection(DeckSection newSection) {
        return newSection == section ? this
                : new DecklistEntry(
                        quantity, name, setCode, collectorNumber, foil, newSection, lineNumber, sourceLine, scryfallId);
    }
}
