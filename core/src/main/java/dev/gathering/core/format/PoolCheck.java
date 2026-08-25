package dev.gathering.core.format;

import dev.gathering.core.card.CardMetadata;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Whether a limited deck is built out of the pool it was drafted from.
 *
 * <p>The question every other check here does not ask. A format asks whether a card is legal;
 * this asks whether it is <em>yours</em> - and in limited that is the whole format. Playing
 * four copies of the best card in the set is legal in limited; it is only impossible because
 * you opened one.
 *
 * <p>Basic lands are free and unlimited, which is the one exception paper limited has always
 * had. Everything else is counted: the deck may hold as many of a card as the pool holds, and
 * not one more.
 *
 * <p>Counted by printing rather than by name, because the pool is a pile of physical cards.
 * Two printings of the same card are two different pieces of cardboard, and a check that
 * treated them as interchangeable would let somebody swap a drafted common for the foil
 * showcase version they already owned.
 *
 * <p>Pure, and separate from {@link DeckValidator} because it needs something a format does
 * not have: what this particular player opened.
 */
public final class PoolCheck {

    private PoolCheck() {
    }

    /**
     * Every card in the deck that the pool does not cover.
     *
     * @param pool every card drafted, one entry per physical card
     * @return an empty list when the deck is built entirely from the pool
     */
    public static List<ValidationIssue> against(ValidatableDeck deck, List<CardMetadata> pool) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (pool == null) {
            return issues;
        }

        Map<String, Integer> available = new LinkedHashMap<>();
        for (CardMetadata card : pool) {
            if (card == null || card.isBasicLand()) {
                continue;
            }
            available.merge(printingKey(card), 1, Integer::sum);
        }

        // The sideboard counts. In limited it is the rest of the pool, and boarding a card in
        // between games puts it in the deck - so a card that is in neither the pool nor a
        // pack is a card that appeared from somewhere either way.
        Map<String, Integer> wanted = new LinkedHashMap<>();
        Map<String, CardMetadata> named = new LinkedHashMap<>();
        for (CardMetadata card : everything(deck)) {
            if (card == null || card.isBasicLand()) {
                continue;
            }
            String key = printingKey(card);
            wanted.merge(key, 1, Integer::sum);
            named.putIfAbsent(key, card);
        }

        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            int have = available.getOrDefault(entry.getKey(), 0);
            int want = entry.getValue();
            if (want <= have) {
                continue;
            }
            CardMetadata card = named.get(entry.getKey());
            String name = card == null || card.name() == null ? "A card" : card.name();
            issues.add(ValidationIssue.error("not_in_pool", have == 0
                    ? name + " is not in this draft pool."
                    : "This pool has " + have + " " + name + "; the deck uses " + want + "."));
        }
        return issues;
    }

    /** Whether the deck is built entirely from the pool, with nothing added. */
    public static boolean isCoveredBy(ValidatableDeck deck, List<CardMetadata> pool) {
        return against(deck, pool).isEmpty();
    }

    private static List<CardMetadata> everything(ValidatableDeck deck) {
        List<CardMetadata> all = new ArrayList<>(deck.deckProper());
        all.addAll(deck.sideboard());
        return all;
    }

    /**
     * One physical card, as far as a pool is concerned.
     *
     * <p>Foil and non-foil are the same printing here. They are different pieces of cardboard
     * in a collection and worth different money, but a draft pool is about which cards you
     * opened - and refusing a deck because somebody sleeved the foil they drafted would be a
     * rule nobody could work out from the message.
     */
    private static String printingKey(CardMetadata card) {
        UUID printing = card.scryfallId();
        return printing != null
                ? printing.toString()
                : "name:" + String.valueOf(card.name()).toLowerCase(java.util.Locale.ROOT);
    }
}
