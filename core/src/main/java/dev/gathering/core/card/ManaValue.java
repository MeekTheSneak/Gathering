package dev.gathering.core.card;

/**
 * What a printed mana cost adds up to.
 *
 * <p>Scryfall sends a converted mana cost with its card data, but a client at a table has only
 * the cost as it is printed - "{2}{U}{U}" - because that is what gets sent to it to draw. So
 * when the table sorts a hand, this is what it sorts by.
 *
 * <p>The awkward part is that a mana symbol is not a character. Hybrid costs, phyrexian mana,
 * the twobrid symbols and X all look like one symbol and count differently, and getting any of
 * them wrong puts a card in the wrong place in somebody's hand every game.
 *
 * <p>Pure: no Minecraft, no network, so every symbol anybody has printed can be checked in
 * milliseconds rather than by looking at a hand.
 */
public final class ManaValue {

    private ManaValue() {
    }

    /**
     * The mana value of a printed cost, or zero for a cost nothing can be read out of.
     *
     * <p>Zero rather than a failure: a land costs nothing, a card whose data has not arrived
     * has no cost to read, and a sort is not the place to start refusing things.
     */
    public static int of(String cost) {
        if (cost == null || cost.isBlank()) {
            return 0;
        }
        int total = 0;
        int at = 0;
        while (at < cost.length()) {
            int open = cost.indexOf('{', at);
            if (open < 0) {
                break;
            }
            int close = cost.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            total += symbol(cost.substring(open + 1, close));
            at = close + 1;
        }
        return total;
    }

    /**
     * What one symbol between braces is worth.
     *
     * <p>A number is that number: "{3}" is three. X, Y and Z are nothing, because a spell on
     * the stack has an X but a card in a hand has not been cast yet. Anything else is one -
     * one coloured pip, one colourless, one snow.
     *
     * <p>A symbol with a slash in it is one symbol that can be paid two ways, and it counts as
     * the more expensive half: "{2/W}" is two, because it can cost two. "{W/U}" and "{U/P}"
     * are both one, because either way of paying them is one symbol.
     */
    private static int symbol(String between) {
        String symbol = between.strip();
        if (symbol.isEmpty()) {
            return 0;
        }
        int most = 0;
        boolean sawAPip = false;
        for (String half : symbol.split("/")) {
            String part = half.strip();
            if (part.isEmpty()) {
                continue;
            }
            if (isVariable(part)) {
                continue;
            }
            Integer number = number(part);
            if (number == null) {
                sawAPip = true;
            } else {
                most = Math.max(most, number);
            }
        }
        return most > 0 ? most : (sawAPip ? 1 : 0);
    }

    /** X, Y and Z, which are nothing until somebody casts the card. */
    private static boolean isVariable(String part) {
        return part.length() == 1 && "XYZxyz".indexOf(part.charAt(0)) >= 0;
    }

    /** The number this symbol is, or null if it is not a number at all. */
    private static Integer number(String part) {
        for (int index = 0; index < part.length(); index++) {
            if (!Character.isDigit(part.charAt(index))) {
                return null;
            }
        }
        try {
            return Integer.valueOf(part);
        } catch (NumberFormatException tooBig) {
            // Not a cost anybody printed. Treated as a pip rather than thrown, because a sort
            // is not somewhere to fail.
            return null;
        }
    }
}
