package dev.gathering.core.ui;

/**
 * Whether a board that has just arrived is one to read the news out of, or one to catch up with quietly.
 * <p>A client that walks away from a table stops being sent boards, and the one it gets on coming back
 * carries a log that has moved on. Reading all of it at once rattled every library on the table at the
 * same instant, rang every card anybody had pointed at and played every sound on top of another.
 * <p>By how much there is to read, not by how long it has been. The rule used to be a gap of eight
 * seconds since the last board - and a table where nothing happens sends nothing, so a player who pointed
 * at a card after a quiet minute had their ping swallowed as a rejoin. The scripted run caught it: the
 * log carried the line, and no ring was ever drawn.
 * <p>Pure.
 */
public final class LogCatchUp {

    /**
     * How many unread lines still count as news.
     * <p>A handful is a table somebody is playing at: a card moved, a counter, a ping. Dozens at once is a
     * log somebody has been away from, and nobody wants a minute of it replayed at them.
     */
    public static final int STILL_NEWS = 12;

    private LogCatchUp() {
    }

    /**
     * @param unread how many lines of the log this client has not read yet
     * @return whether to take them in silently rather than as news
     */
    public static boolean tooMuchToRead(int unread) {
        return unread > STILL_NEWS;
    }
}
