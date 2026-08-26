package dev.gathering.client;

import java.util.Locale;

/**
 * What a counter is called, in one place.
 *
 * <p>Two things write a counter's name at the player - the button that adds one and the card
 * it ends up on - and they had a copy of the rule each. A card reading "+1/+1" under a button
 * reading "Plus one" is two rules where there should be one.
 *
 * <p>Counter names are not translated, because they are not words this mod chose: a table
 * that agrees a card has three "stun" counters has agreed on the word "stun", and a client
 * that renamed it would be describing a different card to its player than to everybody else.
 * All that happens here is that a name is capitalised for the start of a label - and a name
 * that already reads as a symbol, "+1/+1" or "-1/-1", is left exactly as it is.
 *
 * <p>Never shortened. It used to be: a card wrote "+1/+1" as a "+" with the count after it,
 * because the full name is wider than most cards are - so three of them read "+3", which in
 * Magic is a different thing entirely. How many there are is written separately by whatever
 * draws it, which is what lets the count drop to its own line rather than the name losing
 * its end.
 */
public final class CounterText {

    private CounterText() {
    }

    public static String name(String counter) {
        if (counter == null || counter.isEmpty()) {
            return "?";
        }
        return Character.isLetter(counter.charAt(0))
                ? counter.substring(0, 1).toUpperCase(Locale.ROOT) + counter.substring(1)
                : counter;
    }

    /**
     * What a pile of this many of this counter says on a card, if it says one thing.
     *
     * <p>Two +1/+1 counters are a +2/+2 creature, and that is what goes on the card: nobody
     * at a table reads "+1/+1, times two" and does the multiplication in their head every
     * time they look at the board. Charge, stun and loyalty have no arithmetic to do, so
     * they get nothing back here and the caller writes the name with a count beside it.
     *
     * <p>The rule itself is {@link dev.gathering.core.game.CardInstance.Counters}', not this
     * class's - it is a fact about Magic rather than a way of drawing - and it is tested
     * there.
     */
    public static String addedUp(String counter, int howMany) {
        return dev.gathering.core.game.CardInstance.Counters.addedUp(counter, howMany);
    }
}
