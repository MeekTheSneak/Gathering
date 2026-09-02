package dev.gathering.core.game;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.event.LogLine;

/**
 * One line of the session's permanent record.
 * <p>The log is append-only. Undoing something does not delete it - it marks it undone and
 * writes a line saying who rewound what. Nothing ever disappears from the record, which is
 * the whole point of having a record: the log is the honesty layer that replaces physical
 * presence, and a log you can quietly edit is not one.
 * <p>The board is the fold of the records that are still standing.
 */
public sealed interface SessionRecord {

    long sequence();

    /**
     * The public log entry.
     * <p>Event records need the board as it was before them to decide what a card may be
     * called, so the caller passes it; the session keeps the log and can supply it.
     */
    LogLine describe(GameState before);

    /** Something a player did. */
    record EventRecord(long sequence, GameEvent event, boolean undone) implements SessionRecord {

        @Override
        public LogLine describe(GameState before) {
            return event.describe(before);
        }

        public EventRecord asUndone() {
            return undone ? this : new EventRecord(sequence, event, true);
        }

        public boolean isStanding() {
            return !undone;
        }
    }

    /** A rewind, recorded as an event in its own right so the history stays readable. */
    record UndoRecord(long sequence, SeatId requester, int actionCount, boolean unanimous)
            implements SessionRecord {

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(
                    unanimous ? "log.gathering.undo_unanimous" : "log.gathering.undo",
                    requester, actionCount);
        }
    }
}
