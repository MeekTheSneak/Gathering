package dev.gathering.core.scryfall;

import dev.gathering.core.card.CardMetadata;
import java.util.Locale;

/**
 * Ties a card Scryfall returned back to the query that asked for it.
 *
 * <p>Its own class rather than a private helper because positional matching is the obvious
 * wrong answer here and it is wrong quietly: Scryfall returns found cards in request order
 * but drops the ones it could not find, so the moment one card is missing every card after
 * it lines up against the wrong decklist line.
 */
public final class CardQueryMatcher {

    private CardQueryMatcher() {
    }

    public static boolean matches(CardQuery query, CardMetadata card) {
        if (query == null || card == null) {
            return false;
        }
        return switch (query) {
            case CardQuery.ById byId -> byId.id().equals(card.scryfallId());
            case CardQuery.ByName byName -> namesMatch(byName.name(), card);
            case CardQuery.ByNameInSet byNameInSet ->
                    namesMatch(byNameInSet.name(), card) && equalsIgnoreCase(byNameInSet.setCode(), card.setCode());
            case CardQuery.ByPrinting byPrinting ->
                    equalsIgnoreCase(byPrinting.setCode(), card.setCode())
                            && equalsIgnoreCase(byPrinting.collectorNumber(), card.collectorNumber());
        };
    }

    /** Scryfall accepts either half of a double-faced card's name and answers with the whole card. */
    private static boolean namesMatch(String requested, CardMetadata card) {
        if (equalsIgnoreCase(requested, card.name())) {
            return true;
        }
        return card.faces().stream().anyMatch(face -> equalsIgnoreCase(requested, face.name()));
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
