package dev.gathering.core.deck;

import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.ParseProblem;
import java.util.List;
import java.util.Optional;

/**
 * An imported decklist, resolved against Scryfall: real printings, plus an honest account
 * of everything that did not work.
 *
 * <p>A deck with unresolved lines is still a deck. Import shows what failed and lets the
 * player fix it; it does not throw the other ninety-nine cards away.
 */
public record ResolvedDeck(
        String name,
        List<ResolvedCard> cards,
        List<UnresolvedEntry> unresolved,
        List<ParseProblem> problems) {

    public ResolvedDeck {
        cards = cards == null ? List.of() : List.copyOf(cards);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public Optional<String> deckName() {
        return Optional.ofNullable(name);
    }

    public boolean isComplete() {
        return unresolved.isEmpty() && problems.isEmpty();
    }

    public List<ResolvedCard> in(DeckSection section) {
        return cards.stream().filter(card -> card.section() == section).toList();
    }

    /** Physical cards in a section, summing quantities. */
    public int cardCount(DeckSection section) {
        return in(section).stream().mapToInt(ResolvedCard::quantity).sum();
    }

    public int totalCards() {
        return cards.stream().mapToInt(ResolvedCard::quantity).sum();
    }

    /** Lines the importer picked a printing for, which are the ones the chooser offers. */
    public List<ResolvedCard> automaticallyChosenPrintings() {
        return cards.stream().filter(ResolvedCard::printingChosenAutomatically).toList();
    }
}
