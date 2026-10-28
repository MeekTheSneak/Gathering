package dev.gathering.core.card;

import java.util.Locale;
import java.util.UUID;

/**
 * One entry from a printing's {@code all_parts}: another card Scryfall says belongs with it.
 * <p>Scryfall lists everything related under the one key - the token a creature makes, the
 * emblem a planeswalker gives, the halves of a meld pair, and the card itself. Only some of
 * those are things a player puts on a battlefield, so the sorting happens here rather than at
 * each of the places that ask.
 *
 * @param id        the related printing's Scryfall id
 * @param name      what it is called, which is what a token search is given
 * @param typeLine  its printed type line
 * @param component Scryfall's word for the relationship: {@code token}, {@code meld_part},
 *                  {@code meld_result} or {@code combo_piece}
 */
public record RelatedCard(UUID id, String name, String typeLine, String component) {

    public RelatedCard {
        name = name == null ? "" : name;
        typeLine = typeLine == null ? "" : typeLine;
        component = component == null ? "" : component;
    }

    /**
     * Whether this is something the card puts onto the battlefield: a token or an emblem.
     * <p>Emblems are filed under {@code combo_piece} rather than {@code token}, alongside the
     * card itself and the other half of a meld pair, so the type line decides that one. Meld
     * results are deliberately out: melding is moving two cards you already own, not making a
     * new object, and offering it as a thing to create would put a third copy on the table.
     */
    public boolean isMade() {
        if (component.equalsIgnoreCase("token")) {
            return true;
        }
        return component.equalsIgnoreCase("combo_piece") && isEmblem();
    }

    private boolean isEmblem() {
        for (String word : typeLine.split("[^A-Za-z]+")) {
            if (word.toLowerCase(Locale.ROOT).equals("emblem")) {
                return true;
            }
        }
        return false;
    }
}
