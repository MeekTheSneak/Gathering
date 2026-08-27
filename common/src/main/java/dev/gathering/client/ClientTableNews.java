package dev.gathering.client;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.LogArg;
import dev.gathering.core.game.event.LogEntry;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.ui.Shaking;
import dev.gathering.registry.Registered;
import dev.gathering.sound.GatheringSounds;
import net.minecraft.sounds.SoundEvent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * What a table's log says has just happened, turned into something to see and hear.
 *
 * <p>Two things come out of it. A pile somebody shuffled shakes where it stands, and the
 * table makes the noise the thing that happened makes.
 *
 * <p>A shuffle is the one thing a player does that changes nothing anybody can see. No card
 * changes zones, no count moves, and the order it changes is the order nobody is entitled to
 * know - so it is the one move {@link dev.gathering.core.ui.CardTravel} correctly refuses to
 * draw, and until now the only account of it was a line in the log.
 *
 * <p>Taken from the log rather than from the board, because the log is the only place a
 * shuffle appears at all. That is also why it is safe: a log line is the sanitized, public
 * account of an event, already built for everyone at the table to read, and what is taken from
 * it here is a seat and nothing else.
 *
 * <p>Client-only, and touched from the network thread as well as the render thread.
 */
public final class ClientTableNews {

    /** Which library is shaking, and when it started. Keyed by table and seat. */
    private record Stirred(BlockPos table, SeatId seat, Zone zone) {
    }

    private static final Map<Stirred, Long> SHAKING = new HashMap<>();

    /** The last log line each table had already been seen to produce. */
    private static final Map<BlockPos, Long> READ_UP_TO = new HashMap<>();

    /** Which log keys mean what, in one table so a new line cannot be given two meanings. */
    private static final String SHUFFLED = "log.gathering.library_shuffled";

    /** A card, or several, going from a library into a hand. */
    private static final String[] DRAWN = {
        "log.gathering.card_drawn", "log.gathering.cards_drawn",
    };

    /**
     * A card coming off the top of a library to be looked at, binned, or shown.
     *
     * <p>One sound for all of them because they are one gesture: a hand lifting the top card
     * of a deck. What happens to it afterwards is what the log line is for.
     */
    private static final String[] OFF_THE_TOP = {
        "log.gathering.library_looked", "log.gathering.library_milled",
        "log.gathering.library_revealed", "log.gathering.scried",
        "log.gathering.surveilled",
    };

    private ClientTableNews() {
    }

    /**
     * Reads whatever is new in this board's log and starts a shake for each shuffle in it.
     *
     * <p>The first board a table sends starts nothing. A game rejoined mid-way arrives with
     * its whole history, and a player walking up to a table should not be met by every
     * shuffle of the last hour happening at once.
     */
    public static void arrived(BlockPos table, GameView board, long now) {
        BlockPos key = table.immutable();
        // One of each at most: eight cards drawn in one update is a hand being dealt, and
        // eight copies of the same noise on top of one another is a bang.
        java.util.Set<Registered<SoundEvent>> heard = new java.util.LinkedHashSet<>();
        synchronized (ClientTableNews.class) {
            Long readTo = READ_UP_TO.get(key);
            long highest = readTo == null ? -1 : readTo;
            for (LogEntry entry : board.log()) {
                highest = Math.max(highest, entry.sequence());
                if (readTo == null || entry.sequence() <= readTo || entry.undone()) {
                    continue;
                }
                if (entry.key().startsWith(SHUFFLED)) {
                    seatOf(entry).ifPresent(seat ->
                            SHAKING.put(new Stirred(key, seat, Zone.LIBRARY), now));
                    heard.add(GatheringSounds.SHUFFLE);
                } else if (startsWithAny(entry.key(), DRAWN)) {
                    heard.add(GatheringSounds.DRAW);
                } else if (startsWithAny(entry.key(), OFF_THE_TOP)) {
                    heard.add(GatheringSounds.SCRY);
                }
            }
            READ_UP_TO.put(key, highest);
            SHAKING.entrySet().removeIf(entry -> now - entry.getValue() >= Shaking.LASTS);
        }
        for (Registered<SoundEvent> sound : heard) {
            TableSounds.at(key, sound);
        }
    }

    private static boolean startsWithAny(String key, String[] prefixes) {
        for (String prefix : prefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** How long this pile has been shaking, or -1 when it is not. */
    public static long shakingFor(BlockPos table, SeatId seat, Zone zone, long now) {
        synchronized (ClientTableNews.class) {
            Long began = SHAKING.get(new Stirred(table, seat, zone));
            if (began == null) {
                return -1;
            }
            long gone = now - began;
            return gone >= Shaking.LASTS ? -1 : gone;
        }
    }

    public static void forget(BlockPos table) {
        synchronized (ClientTableNews.class) {
            READ_UP_TO.remove(table);
            SHAKING.keySet().removeIf(stirred -> stirred.table().equals(table));
        }
    }

    public static void clear() {
        synchronized (ClientTableNews.class) {
            READ_UP_TO.clear();
            SHAKING.clear();
        }
    }

    /**
     * Whose library was shuffled.
     *
     * <p>The second seat in the line, not the first: the line is "%1$s shuffled %2$s's
     * library", and somebody searching another player's library with permission shuffles a
     * library that is not theirs. The pile that shakes is the one that was shuffled.
     */
    private static java.util.Optional<SeatId> seatOf(LogEntry entry) {
        SeatId last = null;
        for (LogArg arg : entry.args()) {
            if (arg instanceof LogArg.Seat seat) {
                last = seat.seat();
            }
        }
        return java.util.Optional.ofNullable(last);
    }
}
