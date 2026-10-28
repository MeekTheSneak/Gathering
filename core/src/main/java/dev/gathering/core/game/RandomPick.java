package dev.gathering.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Taking some cards at random out of a hand nobody else may see.
 * <p>A discard at random is the one thing at this table a player cannot honestly do for
 * themselves. Every other verb here is trusted - the mod has no rules engine, and if somebody
 * wants to draw eight cards nothing stops them, because nothing stops them across a real
 * table either. But "discard two at random" is different in kind: the whole value of it is
 * that the person doing it did not choose, and a client that picked its own two would be
 * indistinguishable from one that picked its worst two. So the picking is the server's, and
 * this is the part of it worth testing.
 * <p>Distinct, and never more than there are. Taking the same card twice would send one card
 * to the graveyard and claim two; asking for four out of a hand of two takes the two.
 * <p>Pure: the randomness arrives as a function, so the rule is checked against a counter
 * rather than against a dice roll. No Minecraft, no session.
 */
public final class RandomPick {

    /**
     * The most that can be taken in one go.
     * <p>Past any real effect, and short of a number that turns one menu press into a whole
     * hand going away by accident.
     */
    public static final int MOST_AT_ONCE = 20;

    private RandomPick() {
    }

    /**
     * Some of these, chosen at random, in the order they were chosen.
     * <p>A partial shuffle rather than repeated draws, because repeated draws have to check
     * what they have already taken and a check like that is where a duplicate gets in. Each
     * step swaps one of what is left into place, so a card that has been taken cannot be
     * reached again by construction.
     *
     * @param from    the cards to choose between; never reordered
     * @param howMany how many to take, clamped to what there is and to {@link #MOST_AT_ONCE}
     * @param random  gives a number in {@code [0, bound)} - the level's own randomness on the
     *     server, never the session's shuffle seed
     */
    public static <T> List<T> some(List<T> from, int howMany, IntUnaryOperator random) {
        if (from == null || from.isEmpty() || howMany <= 0 || random == null) {
            return List.of();
        }
        int taking = Math.min(Math.min(howMany, MOST_AT_ONCE), from.size());
        List<T> left = new ArrayList<>(from);
        List<T> taken = new ArrayList<>(taking);
        for (int index = 0; index < taking; index++) {
            int bound = left.size() - index;
            int at = index + clamped(random.applyAsInt(bound), bound);
            T chosen = left.get(at);
            left.set(at, left.get(index));
            left.set(index, chosen);
            taken.add(chosen);
        }
        return List.copyOf(taken);
    }

    /**
     * Keeps a number inside the range it was asked for.
     * <p>Nothing here trusts the source to honor its own bound. It is a function passed in,
     * and one that answered out of range would read past the end of a hand - an exception in
     * the middle of somebody's turn, on the server, over a discard.
     */
    private static int clamped(int given, int bound) {
        if (given < 0) {
            return 0;
        }
        return given >= bound ? bound - 1 : given;
    }
}
