package dev.gathering.core.ui;

import dev.gathering.core.game.CardInstance;
import dev.gathering.core.game.visibility.CardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a counter is called, in one place.
 * <p>Two things write a counter's name at the player - the button that adds one and the card
 * it ends up on - and they had a copy of the rule each. A card reading "+1/+1" under a button
 * reading "Plus one" is two rules where there should be one.
 * <p>Counter names are not translated, because they are not words this mod chose: a table
 * that agrees a card has three "stun" counters has agreed on the word "stun", and a client
 * that renamed it would be describing a different card to its player than to everybody else.
 * All that happens here is that a name is capitalized for the start of a label - and a name
 * that already reads as a symbol, "+1/+1" or "-1/-1", is left exactly as it is.
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
     * <p>Two +1/+1 counters are a +2/+2 creature, and that is what goes on the card: nobody
     * at a table reads "+1/+1, times two" and does the multiplication in their head every
     * time they look at the board. Charge, stun and loyalty have no arithmetic to do, so
     * they get nothing back here and the caller writes the name with a count beside it.
     * <p>The rule itself is {@link dev.gathering.core.game.CardInstance.Counters}', not this
     * class's - it is a fact about Magic rather than a way of drawing - and it is tested
     * there.
     */
    public static String addedUp(String counter, int howMany) {
        return CardInstance.Counters.addedUp(counter, howMany);
    }

    /**
     * What goes in the corner where a card prints its own numbers, or null.
     * <p>One corner, so one answer. A written power and toughness wins: typing it is a
     * statement that the printed numbers are wrong. Otherwise loyalty goes there, where the
     * card prints it, and it earns the spot over a line in the counter stack because loyalty
     * is the number a planeswalker <em>is</em>.
     * <p>Nothing decides which a card ought to have. A creature somebody put loyalty on shows
     * loyalty; that is a table's business.
     */
    public static String cornerNumber(CardView card) {
        if (card == null) {
            return null;
        }
        String written = card.writtenStrength().orElse(null);
        if (written != null) {
            return written;
        }
        int loyalty = card.counter(CardInstance.Counters.LOYALTY);
        return loyalty == 0 ? null : Integer.toString(loyalty);
    }

    /**
     * One counter, as the name it is written by and the count beside it.
     * <p>The count is separate so it can be fitted separately, or dropped to its own line, or
     * left off - which is a drawing decision and differs between a board on a screen and a
     * board lying on a block. What must not differ is which counters are shown and what each
     * one is called, which is why that part is here.
     *
     * @param count what goes after the name, or null where the name says it all
     */
    public record Line(String name, String count) {
    }

    /**
     * What a card's counters say, in the order they are written up its bottom edge.
     * <p>Loyalty is left out where it is already in the corner: saying it twice on one card
     * makes a board look busier than it is. Everything else is its name, added up where
     * adding up is what Magic does - two +1/+1 counters are a +2/+2 creature and that is what
     * goes on the card - and otherwise a name with a count beside it.
     */
    public static List<Line> linesOn(CardView card) {
        if (card == null || card.counters().isEmpty()) {
            return List.of();
        }
        // Asked once rather than once per counter. This runs for every card on the table
        // every frame, and writtenStrength answers with an Optional - a cheap thing to make
        // and a silly thing to make thirty times a frame for an answer that cannot change
        // between two counters on the same card.
        boolean loyaltyIsInTheCorner = card.writtenStrength().isEmpty();
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> counter : card.counters().entrySet()) {
            if (CardInstance.Counters.LOYALTY.equals(counter.getKey()) && loyaltyIsInTheCorner) {
                continue;
            }
            String together = addedUp(counter.getKey(), counter.getValue());
            if (together != null) {
                lines.add(new Line(together, null));
                continue;
            }
            String name = name(counter.getKey());
            lines.add(counter.getValue() == 1
                    ? new Line(name, null)
                    : new Line(name, "x" + counter.getValue()));
        }
        return List.copyOf(lines);
    }
}
