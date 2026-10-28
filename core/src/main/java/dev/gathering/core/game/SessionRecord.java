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

    /**
     * A rewind, recorded as an event in its own right so the history stays readable.
     * <p>With where three counters had got to when it was made, because each only ever goes up
     * and a rewind folds the board back past them: the id the next new card would have had, how
     * many shuffles there had been, and how many face-down markers had been handed out. Without
     * them a rewound token's id went to the next card made, a shuffle after a rewind repeated the
     * permutation of the one rewound - so a player who had drawn off it knew the next draw - and a
     * face-down marker went to a second card. All zero in a log written before rewinds kept them,
     * which changes nothing about how that log folds.
     */
    record UndoRecord(long sequence, SeatId requester, int actionCount, boolean unanimous,
            int idsFrom, int shufflesFrom, int markersFrom) implements SessionRecord {

        public UndoRecord {
            idsFrom = Math.max(0, idsFrom);
            shufflesFrom = Math.max(0, shufflesFrom);
            markersFrom = Math.max(0, markersFrom);
        }

        /** A rewind as a log written before rewinds kept their counters has it. */
        public UndoRecord(long sequence, SeatId requester, int actionCount, boolean unanimous) {
            this(sequence, requester, actionCount, unanimous, 0, 0, 0);
        }

        @Override
        public LogLine describe(GameState before) {
            return LogLine.of(
                    unanimous ? "log.gathering.undo_unanimous" : "log.gathering.undo",
                    requester, actionCount);
        }
    }
}
