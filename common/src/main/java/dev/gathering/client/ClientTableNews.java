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
 * <p>Two things come out of it. A pile somebody shuffled shakes where it stands, and the
 * table makes the noise the thing that happened makes.
 * <p>A shuffle is the one thing a player does that changes nothing anybody can see. No card
 * changes zones, no count moves, and the order it changes is the order nobody is entitled to
 * know - so it is the one move {@link dev.gathering.core.ui.CardTravel} correctly refuses to
 * draw, and until now the only account of it was a line in the log.
 * <p>Taken from the log rather than from the board, because the log is the only place a
 * shuffle appears at all. That is also why it is safe: a log line is the sanitized, public
 * account of an event, already built for everyone at the table to read, and what is taken from
 * it here is a seat and nothing else.
 * <p>Client-only, and touched from the network thread as well as the render thread.
 */
public final class ClientTableNews {

    /** Which library is shaking, and when it started. Keyed by table and seat. */
    private record Stirred(BlockPos table, SeatId seat, Zone zone) {
    }

    private static final Map<Stirred, Long> SHAKING = new HashMap<>();

    /**
     * Whose turn it was at each table last time a board arrived.
     * <p>Kept so that "it has become your turn" can be noticed at all. It is a change rather
     * than a state: the board says whose turn it is on every update, and a table that told you
     * so every time would be telling you nothing.
     */
    private static final Map<BlockPos, SeatId> WAS_ACTIVE = new HashMap<>();

    /** When the turn last came round to this client, per table. */
    private static final Map<BlockPos, Long> CAME_ROUND = new HashMap<>();

    /**
     * How long the table says it is your turn before going quiet about it.
     * <p>Long enough to notice on the way back from the kitchen in a game of four, short
     * enough that it is not still on the screen when you have started playing.
     */
    public static final long YOUR_TURN_LASTS = 2_500L;

    /** Which card is being pointed at, and when the finger went down. */
    private record Pointed(BlockPos table, dev.gathering.core.game.CardInstanceId card) {
    }

    private static final Map<Pointed, Long> POINTING = new HashMap<>();

    /** The last log line each table had already been seen to produce. */
    private static final Map<BlockPos, Long> READ_UP_TO = new HashMap<>();

    /** Which log keys mean what, in one table so a new line cannot be given two meanings. */
    private static final String SHUFFLED = "log.gathering.library_shuffled";

    /** The turn handed on - to the next seat, or back to the only one. */
    private static final String PASSED = "log.gathering.turn_passed";

    /**
     * Somebody pointing at a card.
     * <p>Taken from the log like everything else here, and that is what makes it safe: the
     * line is built with {@code CardRef.publicRefFor}, which names a card only when the whole
     * table may see it. A ping of something private carries no id to ring, and rings nothing.
     */
    private static final String POINTED_AT = "log.gathering.pinged";

    /** A card, or several, going from a library into a hand. */
    private static final String[] DRAWN = {
        "log.gathering.card_drawn", "log.gathering.cards_drawn",
    };

    /**
     * A card coming off the top of a library to be looked at, binned, or shown.
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
     * <p>The first board a table sends starts nothing. A game rejoined mid-way arrives with
     * its whole history, and a player walking up to a table should not be met by every
     * shuffle of the last hour happening at once.
     */
    public static void arrived(BlockPos table, GameView board, long now) {
        BlockPos key = table.immutable();
        // One of each at most: eight cards drawn in one update is a hand being dealt, and
        // eight copies of the same noise on top of one another is a bang.
        java.util.Set<Registered<SoundEvent>> heard = new java.util.LinkedHashSet<>();
        // Vanilla's, not the mod's: the mod's sounds are its own audio files and those
        // are the owner's to make. A pling is what every game uses to mean "look here", and
        // it needs nothing added to the resource pack.
        boolean pointed = false;
        boolean passed = false;
        synchronized (ClientTableNews.class) {
            Long readTo = READ_UP_TO.get(key);
            // A log that has run on without this client watching is taken in quietly rather than read
            // out: coming back to a table rattled every library at once, rang every card anybody had
            // pointed at and played every sound on top of another. By how much there is to read
            // ({@link dev.gathering.core.ui.LogCatchUp}), not by how long since the last board: a table
            // where nothing happens sends nothing, so a ping after a quiet minute was swallowed as a
            // rejoin and no ring was ever drawn.
            int unread = 0;
            for (LogEntry entry : board.log()) {
                if (readTo != null && entry.sequence() > readTo && !entry.undone()) {
                    unread++;
                }
            }
            boolean resumed = dev.gathering.core.ui.LogCatchUp.tooMuchToRead(unread);
            long highest = readTo == null ? -1 : readTo;
            for (LogEntry entry : board.log()) {
                highest = Math.max(highest, entry.sequence());
                // Too much missed to replay: the lines are marked read and none of them is sounded.
                // Marked read and then returned, which is what this did, skipped the turn coming round
                // to you as well - and at a table waiting on you no further board arrives, so it was
                // never told at all. The one thing you have to act on is noticed below either way.
                if (resumed || readTo == null || entry.sequence() <= readTo || entry.undone()) {
                    continue;
                }
                if (entry.key().startsWith(SHUFFLED)) {
                    // The noise still happens; the pile stops jumping. Somebody who has asked
                    // for less motion has not asked to be told less - which is the difference
                    // between reducing motion and removing feedback.
                    if (!ClientSettings.reducedMotion()) {
                        seatOf(entry).ifPresent(seat ->
                                SHAKING.put(new Stirred(key, seat, Zone.LIBRARY), now));
                    }
                    heard.add(GatheringSounds.SHUFFLE);
                } else if (startsWithAny(entry.key(), DRAWN)) {
                    heard.add(GatheringSounds.DRAW);
                } else if (startsWithAny(entry.key(), OFF_THE_TOP)) {
                    heard.add(GatheringSounds.SCRY);
                } else if (entry.key().startsWith(PASSED)) {
                    passed = true;
                } else if (entry.key().startsWith(POINTED_AT)) {
                    cardOf(entry).ifPresent(card -> POINTING.put(new Pointed(key, card), now));
                    pointed = true;
                }
            }
            READ_UP_TO.put(key, highest);
            SHAKING.entrySet().removeIf(entry -> now - entry.getValue() >= Shaking.LASTS);
            POINTING.entrySet().removeIf(
                    entry -> now - entry.getValue() >= dev.gathering.core.ui.Pointing.LASTS);
        }
        // The turn coming to you says so in its own sound; any other pass is the table's.
        if (!noticeTheTurn(key, board, now) && passed) {
            TableSounds.turnAt(key, GatheringSounds.PASS_TURN);
        }
        for (Registered<SoundEvent> sound : heard) {
            TableSounds.at(key, sound);
        }
        if (pointed) {
            TableSounds.vanillaAt(key, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value());
        }
    }

    /**
     * Notices the turn coming round to this player, once, when it does, and says whether it did.
     * <p>In a game of four, three of the boards are always somewhere other than where you are
     * looking, and the turn passing to you is the one event you have to act on. The status row
     * has always said whose turn it is; what it could not do is get your attention when that
     * changed, because a row that is always on screen is a row nobody reads on the frame it
     * changes.
     * <p>Only your own turn, only the moment it arrives, and only at a table you are sitting at -
     * a spectator has no turn to be told about. Its sound is the owner's own; any other pass is the
     * table's pass sound, played by the caller from the log, and never both on one moment. A board
     * that arrives with the turn already yours because you have only just walked up says nothing:
     * there was no change, and the first board of a table is not news.
     */
    private static boolean noticeTheTurn(BlockPos table, GameView board, long now) {
        SeatId mine = board.viewer() instanceof dev.gathering.core.game.visibility.Viewer.Seated seated
                ? seated.seat()
                : null;
        SeatId active = board.turn() == null ? null : board.turn().activeSeat();
        SeatId before;
        synchronized (ClientTableNews.class) {
            before = WAS_ACTIVE.put(table, active);
        }
        if (mine == null || active == null || before == null || before.equals(active)) {
            return false;
        }
        if (!active.equals(mine) || !ClientSettings.turnNotification()) {
            return false;
        }
        synchronized (ClientTableNews.class) {
            CAME_ROUND.put(table, now);
        }
        // Through TableSounds, so somebody who has turned the table's noises down is not
        // shouted at by this one.
        TableSounds.turnAt(table, GatheringSounds.YOUR_TURN);
        return true;
    }

    /**
     * How long ago the turn came round to this player here, or -1 if it has not lately.
     * <p>For the screen to say so in words as well as with a noise: a notification that only
     * makes a sound is no notification at all to somebody playing with the sound off, which is
     * a great many people.
     */
    public static long yourTurnSince(BlockPos table, long now) {
        synchronized (ClientTableNews.class) {
            Long when = CAME_ROUND.get(table);
            if (when == null) {
                return -1;
            }
            long gone = now - when;
            return gone >= YOUR_TURN_LASTS ? -1 : gone;
        }
    }

    /**
     * How long this card has been ringed, or -1 when it is not.
     * <p>Asked by both boards while they draw, so it is a lookup rather than a scan.
     */
    public static long pointedAtFor(
            BlockPos table, dev.gathering.core.game.CardInstanceId card, long now) {
        if (table == null || card == null) {
            return -1;
        }
        synchronized (ClientTableNews.class) {
            Long began = POINTING.get(new Pointed(table, card));
            if (began == null) {
                return -1;
            }
            long gone = now - began;
            return gone >= dev.gathering.core.ui.Pointing.LASTS ? -1 : gone;
        }
    }

    /**
     * Which card a line is about, when it names one this client may see.
     * <p>Only {@code ById} counts. A log line about a card nobody at the table is entitled to
     * identify carries {@code Anonymous} instead, and there is nothing to ring - which is the
     * right outcome rather than a gap: a ring around a face-down card would be this client
     * saying which one it is.
     */
    private static java.util.Optional<dev.gathering.core.game.CardInstanceId> cardOf(
            LogEntry entry) {
        for (LogArg arg : entry.args()) {
            if (arg instanceof LogArg.Card card
                    && card.card() instanceof dev.gathering.core.game.event.CardRef.ById byId) {
                return java.util.Optional.of(byId.id());
            }
        }
        return java.util.Optional.empty();
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
            POINTING.keySet().removeIf(pointed -> pointed.table().equals(table));
            WAS_ACTIVE.remove(table);
            CAME_ROUND.remove(table);
        }
    }

    public static void clear() {
        synchronized (ClientTableNews.class) {
            READ_UP_TO.clear();
            SHAKING.clear();
            POINTING.clear();
            WAS_ACTIVE.clear();
            CAME_ROUND.clear();
        }
    }

    /**
     * Whose library was shuffled.
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
