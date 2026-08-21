package dev.gathering.core.game.event;

import java.util.List;

/**
 * One line of the public event log, as data rather than a sentence.
 *
 * <p>The log is the honesty layer that replaces physical presence: it is what makes a remote
 * dispute resolvable, and it is public by default. So it is built here as a translation key
 * plus arguments, and turned into "Chris tapped Halana and Tevesh Szat" by the display layer,
 * which is the only layer that knows what language anyone reads or what a seat is called.
 *
 * <p><b>The rule this type exists to enforce:</b> a log line never carries hidden card
 * identity. Arguments are seats, counts, zone names, and card instance ids - and an instance
 * id is resolved through the reader's own view, so a reader not entitled to a card sees its
 * marker rather than its name. Nothing in a public log entry can be inverted into a secret.
 */
public record LogLine(String key, List<Object> args) {

    public LogLine {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A log line needs a key");
        }
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static LogLine of(String key, Object... args) {
        return new LogLine(key, List.of(args));
    }
}
