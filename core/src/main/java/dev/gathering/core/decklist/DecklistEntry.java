package dev.gathering.core.decklist;

import java.util.Optional;

/**
 * One line of a decklist, parsed but not yet resolved to a printing.
 *
 * <p>The set code and collector number are hints, not identity. Resolution happens later
 * against the Scryfall cache, where a missing or wrong hint falls back to the cheapest
 * matching printing and an ambiguity is offered to the importer rather than guessed at.
 */
public record DecklistEntry(
        int quantity,
        String name,
        String setCode,
        String collectorNumber,
        boolean foil,
        DeckSection section,
        int lineNumber,
        String sourceLine) {

    public Optional<String> set() {
        return Optional.ofNullable(setCode);
    }

    public Optional<String> collector() {
        return Optional.ofNullable(collectorNumber);
    }

    /** Whether this line named a specific printing rather than just a card. */
    public boolean hasPrintingHint() {
        return setCode != null;
    }

    public DecklistEntry withSection(DeckSection newSection) {
        return newSection == section ? this
                : new DecklistEntry(quantity, name, setCode, collectorNumber, foil, newSection, lineNumber, sourceLine);
    }
}
