package dev.gathering.core.card;

import java.util.Optional;

/**
 * Card-shaped things with nothing printed on them.
 * <p>Magic keeps inventing table states that are not cards: the monarch, the initiative, the
 * ring tempting you, a day that becomes a night, whatever the set after this one calls its
 * version. Every one of them is tracked at a real table by putting something on the table -
 * a card from the box, a scrap of paper, the little cardboard thing that came in the pack -
 * and writing on it. So rather than guess at each mechanic and be wrong about the next one,
 * the mod prints blank stock and hands over the pen.
 * <p>Two stocks, because a table already reads them as two different objects:
 *
 * <ul>
 *   <li>{@link #BLANK} is the scrap of paper. Anything a group needs to remember goes on it.</li>
 *   <li>{@link #EMBLEM} is the thing a planeswalker's ultimate leaves behind: it is written
 *       the way Wizards writes one, it cannot be removed by anything, and it is worth its own
 *       look precisely so nobody has to ask which of the cards on the table is the emblem.</li>
 * </ul>
 * <p>These live in the custom-card namespace, which exists so a made-up card can never
 * collide with a Scryfall printing. Nothing looks them up, nothing fetches art for them, and
 * a client draws them from what is written on them - see the note on {@link CardIdentity}.
 */
public enum PaperStock {

    /** Blank stock. Whatever the table needs to remember, in the table's own words. */
    BLANK("blank"),

    /** An emblem. The same object, read as the thing an ultimate leaves behind. */
    EMBLEM("emblem");

    /**
     * What every stock's custom id starts with.
     * <p>Its own prefix inside the custom namespace so that a server which one day hosts real
     * custom cards cannot name one {@code emblem} and have the client draw it as blank stock.
     */
    private static final String PREFIX = "paper/";

    /**
     * The stocks, once.
     * <p>{@code values()} hands back a fresh copy of the array every time it is called, and
     * this is asked of every card the board draws on every frame - so it is asked here once
     * instead. Cheap either way; free is better in a render loop.
     */
    private static final PaperStock[] ALL = values();

    private final String id;

    PaperStock(String id) {
        this.id = id;
    }

    /** The identity a card of this stock carries. Never foil: there is no printing to foil. */
    public CardIdentity identity() {
        return CardIdentity.ofCustom(PREFIX + id, false);
    }

    /** Which stock this is, or empty for anything that is a real card. */
    public static Optional<PaperStock> of(CardIdentity identity) {
        if (identity == null || !identity.isCustom()) {
            return Optional.empty();
        }
        String custom = identity.customId();
        for (PaperStock stock : ALL) {
            if ((PREFIX + stock.id).equals(custom)) {
                return Optional.of(stock);
            }
        }
        return Optional.empty();
    }
}
