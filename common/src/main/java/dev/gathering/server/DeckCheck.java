package dev.gathering.server;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.format.DeckValidator;
import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.ValidatableDeck;
import dev.gathering.core.format.ValidationResult;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The deck check, run against a deck somebody is about to put down.
 *
 * <p>The one referee this mod permits, and until now it was a class nobody called: the
 * validator was written, tested and then never wired to anything, so a thirty-two card deck
 * started a game of Modern without a word. It runs here, once, when a deck is committed to a
 * table with a format on it - and then it is over. Nothing in it is consulted during play.
 *
 * <p><b>Out of the cache and never off the network.</b> This is on the server thread with a
 * player waiting, and a deck check that fetched a hundred cards from Scryfall would hang the
 * server for as long as that took. Every card in a deck somebody built through this mod is in
 * the cache already, because that is where it came from, and the cache's index is warmed on
 * server start - so in practice this is a hundred map lookups. A deck committed in the first
 * seconds of a server, before the warm finishes, falls through to the cache files instead;
 * that is a hundred small reads and it happens once.
 *
 * <p>A card that is <em>not</em> cached is a card this check cannot judge, and an unjudgeable
 * card makes the whole answer unjudgeable rather than making the deck illegal. Refusing a deck
 * because a server had forgotten what one of its cards was would be the mod inventing a rules
 * violation, which is exactly what it promises not to do.
 */
public final class DeckCheck {

    private DeckCheck() {
    }

    /**
     * What this deck comes to against this format, or nothing when it cannot be told.
     *
     * <p>Empty means "no opinion": a free-play table with no format, no metadata service, or a
     * deck with a card the server has never looked up. All three are reasons to let the game
     * start, and none of them is a reason to claim a deck is legal either.
     */
    public static Optional<ValidationResult> of(DeckComponent deck, FormatPreset format) {
        return of(deck, format, null);
    }

    /**
     * The same, against the pool this deck was drafted from.
     *
     * <p>A pool is the one thing a format cannot tell you. Every other check here asks
     * whether a card is legal; this asks whether it is yours, which in limited is the whole
     * format - four copies of the best card in the set is a fine limited deck and impossible
     * because nobody opens four. A deck with no pool is judged on its format alone, which is
     * every deck anybody imported.
     */
    public static Optional<ValidationResult> of(
            DeckComponent deck, FormatPreset format, DraftedPool pool) {
        if (deck == null || format == null) {
            return Optional.empty();
        }
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            return Optional.empty();
        }
        List<CardMetadata> mainboard = lookUp(cards, deck.entries());
        List<CardMetadata> commanders = lookUp(cards, deck.commanders());
        List<CardMetadata> sideboard = lookUp(cards, deck.sideboard());
        if (mainboard == null || commanders == null || sideboard == null) {
            return Optional.empty();
        }
        ValidatableDeck checkable =
                new ValidatableDeck(deck.name(), mainboard, commanders, sideboard);
        ValidationResult result = DeckValidator.validate(checkable, format);
        if (pool == null || pool.isEmpty()) {
            return Optional.of(result);
        }
        List<CardMetadata> drafted = lookUp(cards, pool.cards());
        if (drafted == null) {
            // A pool with a card the server cannot look up is a pool this check cannot judge,
            // and an unjudgeable pool must not become an accusation. The format check stands.
            return Optional.of(result);
        }
        List<dev.gathering.core.format.ValidationIssue> issues =
                new ArrayList<>(result.issues());
        issues.addAll(dev.gathering.core.format.PoolCheck.against(checkable, drafted));
        return Optional.of(new ValidationResult(format, issues));
    }

    /** Every card in a section, or null if the cache cannot answer for one of them. */
    private static List<CardMetadata> lookUp(CardDataService cards, List<CardComponent> section) {
        List<CardMetadata> found = new ArrayList<>(section.size());
        for (CardComponent card : section) {
            UUID printing = card.scryfallId().orElse(null);
            if (printing == null) {
                // A card somebody named by hand rather than one off Scryfall. There is nothing
                // to check it against, so there is nothing to say about the deck it is in.
                return null;
            }
            CardMetadata metadata = cards.store().find(CardQuery.byId(printing)).orElse(null);
            if (metadata == null) {
                return null;
            }
            found.add(metadata);
        }
        return found;
    }
}
