package dev.gathering.core.format;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.deck.ResolvedCard;
import dev.gathering.core.deck.ResolvedDeck;
import java.util.ArrayList;
import java.util.List;

/**
 * A deck flattened for checking: one entry per physical card.
 * <p>Flattened rather than quantity-bearing because every question the validator asks is
 * about cards rather than about lines - "how many copies of this" is a count, and counting a
 * list is simpler than summing quantities and getting it subtly wrong.
 */
public record ValidatableDeck(
        String name,
        List<CardMetadata> mainboard,
        List<CardMetadata> commanders,
        List<CardMetadata> sideboard) {

    public ValidatableDeck {
        mainboard = List.copyOf(mainboard);
        commanders = List.copyOf(commanders);
        sideboard = List.copyOf(sideboard);
    }

    public static ValidatableDeck from(ResolvedDeck deck) {
        return new ValidatableDeck(
                deck.deckName().orElse("Deck"),
                flatten(deck, DeckSection.MAINBOARD),
                flatten(deck, DeckSection.COMMANDER),
                flatten(deck, DeckSection.SIDEBOARD));
    }

    /** Mainboard plus command zone: what counts toward a deck's size. */
    public List<CardMetadata> deckProper() {
        List<CardMetadata> all = new ArrayList<>(mainboard.size() + commanders.size());
        all.addAll(mainboard);
        all.addAll(commanders);
        return all;
    }

    public int size() {
        return mainboard.size() + commanders.size();
    }

    private static List<CardMetadata> flatten(ResolvedDeck deck, DeckSection section) {
        List<CardMetadata> cards = new ArrayList<>();
        for (ResolvedCard card : deck.in(section)) {
            for (int copy = 0; copy < card.quantity(); copy++) {
                cards.add(card.metadata());
            }
        }
        return cards;
    }
}
