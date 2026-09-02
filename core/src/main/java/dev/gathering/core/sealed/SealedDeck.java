package dev.gathering.core.sealed;

import dev.gathering.core.card.CardIdentity;
import java.util.List;
import java.util.Locale;

/**
 * A deck a product comes with, as its cards.
 * <p>A Commander precon, a starter kit's two decks, a bundle's land pack. The published data
 * names one of these on the product and lists it elsewhere in the same file, so a shop that
 * only read the product would know a deck was in the box and not what was in the deck.
 * <p>Expanded rather than counted: four Forests are four entries. Every other list of cards
 * in the mod is a list of cards, and a deck that was the one exception would be a special
 * case in the deck item, the table, the collection and the shop all at once.
 * <p>Pure.
 */
public record SealedDeck(
        String name,
        String setCode,
        List<CardIdentity> commanders,
        List<CardIdentity> mainboard,
        List<CardIdentity> sideboard) {

    public SealedDeck {
        name = name == null ? "" : name.trim();
        setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        commanders = commanders == null ? List.of() : List.copyOf(commanders);
        mainboard = mainboard == null ? List.of() : List.copyOf(mainboard);
        sideboard = sideboard == null ? List.of() : List.copyOf(sideboard);
    }

    /** How many cards altogether, across every section. */
    public int size() {
        return commanders.size() + mainboard.size() + sideboard.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Whether a name and a set are this deck.
     * <p>By name because that is what a product names it by, and by set because two sets have
     * both had a deck called "Peace Offering" and will again.
     */
    public boolean is(String wantedSet, String wantedName) {
        return setCode.equalsIgnoreCase(wantedSet == null ? "" : wantedSet.trim())
                && name.equalsIgnoreCase(wantedName == null ? "" : wantedName.trim());
    }
}
