package dev.gathering.core.game;

import dev.gathering.core.card.CardMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finding a basic land in a library.
 *
 * <p>Fetching a land means going and getting one out of your own deck. It does not mean
 * conjuring one: a Forest that was never in your deck is a Forest you did not build for, and
 * a table where anybody can call up any land whenever they like is not playing the same game
 * as the person who ran twelve of them on purpose.
 *
 * <p>So this looks through the library and says which cards are the one asked for. If there
 * are none, the answer is none - the whole point being that a deck with no Forests in it
 * cannot fetch a Forest.
 *
 * <p>Matched on the printed name <em>and</em> on being a basic land, because a card can be
 * called Forest without being one and a deck can hold a Snow-Covered Forest that is a
 * different card. Nothing here decides what happens next; it finds cards.
 *
 * <p>Pure: no Minecraft, no session, so which cards count is checked rather than played.
 */
public final class BasicLands {

    private BasicLands() {
    }

    /**
     * Where in the library the wanted basics are, top first, at most this many.
     *
     * <p>Positions rather than cards, because the caller holds the real library as instance
     * ids and this only ever sees what those cards are - the two lists are parallel and the
     * position is what joins them.
     *
     * @param library what each card in the library is, top first; a null entry is a card this
     *     server has never looked up and is passed over rather than guessed at
     * @param printedName the land's name exactly as it is printed
     * @param howMany the most to find; fewer come back when the deck holds fewer
     */
    public static List<Integer> findIn(
            List<CardMetadata> library, String printedName, int howMany) {
        if (library == null || printedName == null || printedName.isBlank() || howMany <= 0) {
            return List.of();
        }
        String wanted = printedName.strip().toLowerCase(Locale.ROOT);
        List<Integer> found = new ArrayList<>();
        for (int at = 0; at < library.size() && found.size() < howMany; at++) {
            if (is(library.get(at), wanted)) {
                found.add(at);
            }
        }
        return List.copyOf(found);
    }

    /**
     * Whether this card is that basic land.
     *
     * <p>Both halves matter. The name alone would take a Snow-Covered Forest, which is a
     * different card somebody chose to run; the type alone would take any basic at all when
     * the player asked for a Forest.
     */
    private static boolean is(CardMetadata card, String wanted) {
        if (card == null || card.name() == null || card.typeLine() == null) {
            return false;
        }
        return card.name().strip().toLowerCase(Locale.ROOT).equals(wanted)
                && card.typeLine().toLowerCase(Locale.ROOT).contains("basic land");
    }
}
