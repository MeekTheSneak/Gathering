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
     * The name and how many there are, the way it is said out loud.
     *
     * <p>The name is never shortened to fit. It used to be - a card with three +1/+1 counters
     * on it wrote "+3", because the full name is wider than most cards are - and in Magic
     * "+3" is a different thing entirely. Whatever draws this trims it to the room it has,
     * so a narrow card loses the end of a long name rather than the sign that says what it is.
     */
    public static String withCount(String counter, int amount) {
        String written = name(counter);
        return amount == 1 ? written : written + " x" + amount;
    }
}
