package dev.gathering.core.game.event;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;

/**
 * One piece of a log line, typed so the display layer knows what it is looking at.
 *
 * <p>{@link LogLine} builds its arguments as plain objects, which is right where they are
 * written - a call site should be able to pass a seat and a number without ceremony. But the
 * log has to cross a network and be turned into a sentence in somebody's language, and "an
 * object that might be a seat" cannot do either. So this is the same information, sorted into
 * the four things a log argument is ever allowed to be.
 *
 * <p>Note what is not here: a card identity. A card only ever appears as a {@link CardRef},
 * which is resolved through the reader's own view, so a reader not entitled to a card sees its
 * marker or the word "a card" rather than its name. There is no shape in this type that can
 * carry a secret.
 */
public sealed interface LogArg {

    /** A seat, resolved to whoever is sitting in it by whoever is reading. */
    record Seat(SeatId seat) implements LogArg {
    }

    /** A card, as strongly as the whole table is entitled to know it. */
    record Card(CardRef card) implements LogArg {
    }

    /** A count, a delta, a turn number. */
    record Amount(int value) implements LogArg {
    }

    /** A zone, translated by the reader rather than named in English here. */
    record Where(Zone zone) implements LogArg {
    }

    /**
     * Text that is already what it is: a player's name, a counter's name, a placement label.
     *
     * <p>Never translated, because none of these are words this mod chose.
     */
    record Text(String text) implements LogArg {
    }

    /**
     * Sorts one of {@link LogLine}'s raw arguments into its shape.
     *
     * <p>Anything unrecognized becomes its own text rather than an exception. A log line that
     * renders a little bluntly is a far better failure than a session that will not broadcast,
     * and the alternative is that adding an argument type to one event breaks every table.
     */
    static LogArg of(Object raw) {
        return switch (raw) {
            case null -> new Text("");
            case LogArg already -> already;
            case SeatId seat -> new Seat(seat);
            case CardRef card -> new Card(card);
            case Zone zone -> new Where(zone);
            case Integer number -> new Amount(number);
            case Long number -> new Amount(number.intValue());
            default -> new Text(String.valueOf(raw));
        };
    }
}
