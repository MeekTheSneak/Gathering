package dev.gathering.core.game;

/**
 * What a counter on a card or a seat may be called.
 * <p>Free text a player types, so it is cleaned and cut like every other piece of free text
 * in a game - a note, a strength, a card's story. Counters were the one that was not, and a
 * counter name is worse than the others to leave open, because it is also a <em>key</em>:
 * a new name is a new entry rather than a replaced one, so a client could add as many as it
 * liked, each as long as it liked, and every one of them is written into the board and saved
 * with the table.
 * <p>The board goes out as one payload with a megabyte bound on it, and that bound throws
 * when it is passed rather than truncating. So a board grown past it is a board that can
 * never be sent to anybody again - and because counters are saved with the session, restarting
 * the server does not fix it. That is a table anybody at it can permanently break, which is
 * the one thing a griefing bound has to stop.
 * <p>Twenty-four characters and twenty-four names per card. Real counters are "+1/+1",
 * "loyalty", "charge", "poison" - a handful of short words - so this is generous by an order
 * of magnitude and still leaves a board that could not approach the bound if every card on it
 * were full.
 */
public final class CounterName {

    /** As long as a counter's name may be. */
    public static final int LONGEST = 24;

    /** As many different counters as one card or one seat may carry. */
    public static final int MOST_PER_CARD = 24;

    private CounterName() {
    }

    /**
     * The name as it will be kept, or null when there is nothing left of it.
     * <p>Cut rather than refused, exactly as a note is. A refusal here would have to travel
     * back out of a payload handler, and "your counter name was too long" is not worth a
     * round trip when the honest answer is the first twenty-four characters of it.
     */
    public static String kept(String written) {
        String tidy = PlayerText.oneLine(written, LONGEST);
        return tidy == null || tidy.isEmpty() ? null : tidy;
    }
}
