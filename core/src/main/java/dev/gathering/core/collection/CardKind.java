package dev.gathering.core.collection;

import java.util.List;
import java.util.Locale;

/**
 * The pile a card goes in when a deck is laid out to be looked at.
 *
 * <p>Not a rules concept and not one the game ever consults - it is how a deck builder groups
 * a list so a person can read it, which is by what the card is. The order below is the order
 * the groups are drawn in, and it is the order every deck site uses: the things that cost mana
 * first, roughly in the order you cast them, then the lands that pay for them.
 *
 * <p>Read off the printed type line rather than from a field, because the type line is what
 * the mod already has for every card and a card's own line is the authority on what it is.
 * A card with several types lands in the first pile that matches, which is why the order here
 * is also a priority: an artifact creature is a creature, because that is the pile a player
 * looks for it in.
 */
public enum CardKind {
    COMMANDER("commander"),
    CREATURE("creature"),
    PLANESWALKER("planeswalker"),
    INSTANT("instant"),
    SORCERY("sorcery"),
    ARTIFACT("artifact"),
    ENCHANTMENT("enchantment"),
    BATTLE("battle"),
    LAND("land"),
    OTHER("other");

    private final String id;

    CardKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "deck.gathering.kind." + id;
    }

    /**
     * Which pile this type line belongs in.
     *
     * <p>Everything before the em dash, so "Artifact Creature - Golem" is read on its types
     * and not on the golem. Matched in the order the constants are declared, which is a
     * priority: the first pile that matches wins, so an artifact creature is a creature.
     *
     * <p>A line that says nothing recognizable gets {@link #OTHER} rather than being dropped.
     * A deck builder that silently loses a card because Scryfall printed a type it has never
     * heard of is worse than one with a pile called "other" in it.
     */
    public static CardKind of(String typeLine) {
        if (typeLine == null || typeLine.isBlank()) {
            return OTHER;
        }
        int dash = typeLine.indexOf('—');
        String types = (dash < 0 ? typeLine : typeLine.substring(0, dash)).toLowerCase(Locale.ROOT);
        for (CardKind kind : SEARCHED) {
            if (types.contains(kind.id)) {
                return kind;
            }
        }
        return OTHER;
    }

    /**
     * The kinds a type line is actually tested against, in priority order.
     *
     * <p>Commander is not one of them: it is a job a card has been given rather than something
     * printed on it, so nothing can be sorted into that pile by reading its line. Other is not
     * one either - it is what is left when none of these matched, and "other" appears in no
     * type line anybody has printed.
     */
    private static final List<CardKind> SEARCHED = List.of(
            CREATURE, PLANESWALKER, INSTANT, SORCERY, ARTIFACT, ENCHANTMENT, BATTLE, LAND);
}
