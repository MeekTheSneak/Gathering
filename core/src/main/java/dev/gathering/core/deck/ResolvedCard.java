package dev.gathering.core.deck;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.DecklistEntry;

/**
 * A decklist line that became real cards: a printing, its metadata, and how many.
 *
 * @param printingChosenAutomatically true when the line named a card rather than a
 *        printing, so the import screen knows to offer a chooser for this line and to
 *        leave the others alone
 */
public record ResolvedCard(
        CardIdentity identity,
        CardMetadata metadata,
        int quantity,
        DeckSection section,
        DecklistEntry source,
        boolean printingChosenAutomatically) {

    public String name() {
        return metadata.name();
    }

    /** The same line resolved to a different printing, which is what the chooser produces. */
    public ResolvedCard withPrinting(CardMetadata replacement) {
        return new ResolvedCard(
                CardIdentity.ofPrinting(replacement.scryfallId(), identity.foil()),
                replacement,
                quantity,
                section,
                source,
                false);
    }
}
