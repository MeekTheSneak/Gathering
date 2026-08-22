package dev.gathering.core.game.event;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the log as it goes out to a table.
 *
 * <p>A {@link LogLine} with its arguments sorted into {@link LogArg}s and stamped with the
 * sequence number of the event that produced it. The sequence is what lets a client tell a
 * line it has already drawn from a new one, and what lets a rewind be shown as a rewind rather
 * than as the log mysteriously getting shorter.
 *
 * @param undone whether the event behind this line has been rewound; kept rather than removed,
 *               because a log that quietly loses entries is not a record of what happened
 */
public record LogEntry(long sequence, String key, List<LogArg> args, boolean undone) {

    public LogEntry {
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static LogEntry of(long sequence, LogLine line, boolean undone) {
        List<LogArg> args = new ArrayList<>(line.args().size());
        for (Object raw : line.args()) {
            args.add(LogArg.of(raw));
        }
        return new LogEntry(sequence, line.key(), args, undone);
    }

    public LogEntry asUndone() {
        return undone ? this : new LogEntry(sequence, key, args, true);
    }
}
