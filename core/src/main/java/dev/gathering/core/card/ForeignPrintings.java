package dev.gathering.core.card;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which printings in another language a player is handed.
 * <p>By the owner's rule: a card printed only in another language is a card like any other - the
 * Japanese bonus sheet in an English set, a promo handed out at a tournament in Japan - and belongs in
 * packs, archives and the printing chooser. Another language's copy of a card that is normally
 * English does not: a French Renaissance copy or a Spanish Foreign Black Border copy of a card is
 * not what anybody means by that card.
 * <p>Two ways of telling them apart, for the two places a printing is chosen. Across one card's
 * printings, the picture: a copy shares its artwork with an English printing, a card of its own does
 * not. Within one set, the set: a set with English printings in it is an English set, and anything in
 * another language in it is a special printing of that set; a promo set is promos, whatever their
 * language; a set wholly in another language that is neither is a set of copies.
 * <p>Pure.
 */
public final class ForeignPrintings {

    /** The set types whose printings are each their own thing, in whatever language. */
    private static final Set<String> OWN_THINGS = Set.of("promo");

    private ForeignPrintings() {
    }

    /**
     * A card's printings without another language's copies of its English ones.
     * <p>For the printing chooser, sorted cheapest first: a Japanese or Spanish copy could otherwise
     * head the list and be what a decklist resolved to.
     */
    public static List<CardMetadata> withoutCopies(List<CardMetadata> printings) {
        if (printings == null) {
            return List.of();
        }
        Set<String> englishPictures = new HashSet<>();
        for (CardMetadata printing : printings) {
            if (printing != null && printing.isEnglish() && !printing.illustrationId().isEmpty()) {
                englishPictures.add(printing.illustrationId());
            }
        }
        return printings.stream()
                .filter(printing -> printing != null && (printing.isEnglish()
                        || printing.illustrationId().isEmpty() || !englishPictures.contains(printing.illustrationId())))
                .toList();
    }

    /**
     * One set's printings, as a pack or an archive of that set holds them: all of them in an English
     * set or a promo set, and only its English ones in a set of copies in another language.
     *
     * @param setType the set's type, as Scryfall writes it
     */
    public static List<CardMetadata> keptIn(Collection<CardMetadata> setPrintings, String setType) {
        if (setPrintings == null) {
            return List.of();
        }
        boolean englishSet = setPrintings.stream().anyMatch(printing -> printing != null && printing.isEnglish());
        boolean ownThings = setType != null && OWN_THINGS.contains(setType);
        return setPrintings.stream()
                .filter(printing -> printing != null && (printing.isEnglish() || englishSet || ownThings))
                .toList();
    }
}
