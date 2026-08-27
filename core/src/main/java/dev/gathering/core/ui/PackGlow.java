package dev.gathering.core.ui;

import dev.gathering.core.card.Rarity;
import java.util.Collection;

/**
 * What color comes out of a pack as it is torn open.
 *
 * <p>The whole of the ceremony's suspense, in one number. A pack is opened for one moment -
 * the one where you find out whether there is anything in it - and every real ritual around
 * opening boosters is about drawing that moment out: turning the pack over, going through the
 * commons first, leaving the rare until last. Light coming out of the tear says whether it
 * was worth it before a single card is shown, which is the same information the ritual is
 * for and none of the waiting.
 *
 * <p>Decided by the best card in the pack rather than by any particular slot, because a rare
 * that came out of the wildcard slot is exactly as good as one that came out of the rare
 * slot, and a player does not care which sheet it was cut from.
 *
 * <p>Pure.
 */
public final class PackGlow {

    /** A mythic in there. Orange, and warmer than the rare. */
    public static final int MYTHIC_LIGHT = 0xFFFF7A18;

    /** A rare in there. */
    public static final int RARE_LIGHT = 0xFFFFD24A;

    /** Nothing above an uncommon. No light rather than a duller light: an honest nothing. */
    public static final int NO_LIGHT = 0x00000000;

    private PackGlow() {
    }

    /**
     * The color for the best card in a pack, or {@link #NO_LIGHT} if there is nothing to say.
     *
     * <p>Special and bonus - The List, a Special Guest - glow as rares. They are the card the
     * pack was opened for as surely as a rare is, and a slot that produced the most exciting
     * card in the pack in silence would read as the pack having produced nothing.
     */
    public static int forPack(Collection<Rarity> rarities) {
        return forRarity(best(rarities));
    }

    /** The color one rarity glows, on its own. */
    public static int forRarity(Rarity rarity) {
        if (rarity == null) {
            return NO_LIGHT;
        }
        return switch (rarity) {
            case MYTHIC -> MYTHIC_LIGHT;
            case RARE, SPECIAL, BONUS -> RARE_LIGHT;
            default -> NO_LIGHT;
        };
    }

    /** Whether anything at all comes out of the tear. */
    public static boolean glows(Collection<Rarity> rarities) {
        return forPack(rarities) != NO_LIGHT;
    }

    /**
     * The best card in the pack.
     *
     * <p>Ranked here rather than by the enum's own order, which is Scryfall's listing order
     * and puts special and bonus past mythic - true of how they are written down and not of
     * how they are opened.
     */
    public static Rarity best(Collection<Rarity> rarities) {
        Rarity best = null;
        int bestRank = 0;
        if (rarities != null) {
            for (Rarity rarity : rarities) {
                int rank = rankOf(rarity);
                if (rank > bestRank) {
                    bestRank = rank;
                    best = rarity;
                }
            }
        }
        return best;
    }

    /**
     * How good a card of this rarity is to open, which is not the order rarities are listed
     * in - Scryfall writes special and bonus after mythic, and neither is better than one.
     *
     * <p>Nought for a rarity nothing is known about, so a card whose rarity did not arrive
     * never outranks one whose did. A pack of nothing but those has no best card at all,
     * which comes back as nothing rather than as a card nobody can describe.
     *
     * <p>Public because the reveal order asks the same question: which card is best is one
     * rule, and the glow color and the last-card-last ordering must never disagree about it.
     */
    public static int rankOf(Rarity rarity) {
        if (rarity == null) {
            return 0;
        }
        return switch (rarity) {
            case MYTHIC -> 5;
            case SPECIAL, BONUS -> 4;
            case RARE -> 3;
            case UNCOMMON -> 2;
            case COMMON -> 1;
            default -> 0;
        };
    }
}
