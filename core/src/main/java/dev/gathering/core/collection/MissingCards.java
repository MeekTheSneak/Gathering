package dev.gathering.core.collection;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.SetRelease;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which cards of a set a collection has not got.
 *
 * <p>{@link SetCompletion} says a collection is one card into a set of three hundred and
 * seventy-three. This says which three hundred and seventy-two. That is the click nobody could
 * make: a number with nothing behind it is a scoreboard, and what somebody sitting at their
 * binder wants is a list to go and find.
 *
 * <p>Counted by exactly the rules the number was counted by, or the two would disagree in
 * front of somebody who can now see both at once. So: only the numbered cards, only up to the
 * set's printed size, one slot per {@linkplain SetRelease#leadingNumber leading number}, and
 * either finish fills a slot. A screen saying "372 missing" over a list of 380 rows would be
 * worse than no list.
 *
 * <p><b>One row per slot, not per printing.</b> A slot that several printings could fill is
 * one thing to go and find, so it is one row - shown as the lowest-numbered printing of it,
 * which is the ordinary one rather than a showcase.
 *
 * <p>Pure: arithmetic over what a set is and what somebody owns.
 */
public record MissingCards(String code, String name, List<Card> cards) {

    /** One card still to find. */
    public record Card(int number, String name, Rarity rarity, UUID printing) {

        public Card {
            name = name == null || name.isBlank() ? "?" : name;
            rarity = rarity == null ? Rarity.COMMON : rarity;
        }
    }

    public MissingCards {
        code = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        name = name == null || name.isBlank() ? code.toUpperCase(Locale.ROOT) : name;
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /** Nothing known about that set, which is different from owning all of it. */
    public static final MissingCards NONE = new MissingCards("", "", List.of());

    /** How many are still to find. */
    public int count() {
        return cards.size();
    }

    /**
     * Works out what is missing.
     *
     * <p>Both halves come from outside: every printing the set has, and every printing the
     * collection holds. What this does is decide which of the first the second fills.
     *
     * @param set        what the set is, from Scryfall's list of them
     * @param everyPrinting every card carrying that set's code, numbered or not
     * @param owned      the cards in the collection that carry that set's code
     */
    public static MissingCards of(
            SetRelease set, Collection<CardMetadata> everyPrinting, Collection<CardMetadata> owned) {
        if (set == null || everyPrinting == null) {
            return NONE;
        }
        Set<Integer> filled = new java.util.HashSet<>();
        if (owned != null) {
            for (CardMetadata card : owned) {
                if (card != null && set.numbers(card.collectorNumber())) {
                    filled.add(SetRelease.leadingNumber(card.collectorNumber()));
                }
            }
        }

        // The lowest-numbered printing of each slot, which is the plain one: a slot shown as
        // its borderless printing would send somebody looking for the wrong card.
        Map<Integer, CardMetadata> plainest = new LinkedHashMap<>();
        for (CardMetadata card : everyPrinting) {
            if (card == null || !set.numbers(card.collectorNumber())) {
                continue;
            }
            int slot = SetRelease.leadingNumber(card.collectorNumber());
            if (filled.contains(slot)) {
                continue;
            }
            CardMetadata already = plainest.get(slot);
            if (already == null
                    || card.collectorNumber().compareTo(already.collectorNumber()) < 0) {
                plainest.put(slot, card);
            }
        }

        List<Card> rows = new ArrayList<>(plainest.size());
        plainest.forEach((slot, card) -> rows.add(
                new Card(slot, card.name(), card.rarity(), card.scryfallId())));
        // In the order they are printed in, which is the order a set is laid out in and the
        // order somebody ticking one off a list reads it in.
        rows.sort(java.util.Comparator.comparingInt(Card::number).thenComparing(Card::name));
        return new MissingCards(set.code(), set.name(), rows);
    }
}
