package dev.gathering.client;

import dev.gathering.core.game.event.LogEntry;
import dev.gathering.core.game.visibility.GameView;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The last die or coin this table saw, held long enough to be looked at.
 * <p>Reported as "need visuals for rolling dice and flipping coins as well as a visual
 * announcement outside of the log". A roll was a line of text in a panel players keep closed,
 * which is the one place a result must not be: the whole reason the server rolls is that a
 * player rolling their own die is a player making a claim, and a result nobody at the table
 * actually saw is exactly as good as a claim.
 * <p>Read off the board's own log rather than sent as anything new. The log already arrives
 * with every board and is already the public record of what happened, so a roll is announced
 * from the same fact the log is written from - there is no second channel to disagree with it,
 * and a player who joins mid-roll simply misses the flourish rather than seeing a stale one.
 * <p>Client-only. Touched from the render thread alone: {@link #seen} is called while drawing
 * the board that carried the entry.
 */
public final class ClientTableRolls {

    /** How long a result stays up. Long enough to look up at, short enough to get out of the way. */
    public static final long SHOWN_MILLIS = 3_500L;

    /** Which log lines are a roll. Everything here is a result the whole table is entitled to. */
    private static final List<String> ROLL_KEYS = List.of(
            "log.gathering.rolled",
            "log.gathering.flipped_heads",
            "log.gathering.flipped_tails",
            "log.gathering.planar_blank",
            "log.gathering.planar_chaos",
            "log.gathering.planar_walk");

    /** One result, and when this client first drew a board carrying it. */
    public record Shown(BlockPos table, LogEntry entry, long at) {
    }

    private static Shown showing;

    private ClientTableRolls() {
    }

    /**
     * Takes the newest roll off this board's log, if it is one this client has not shown yet.
     * <p>The newest and only the newest: a board that arrives after a burst of rolls announces
     * the last of them rather than replaying the burst, which is what somebody watching the
     * table would have caught up on anyway.
     * <p>Sequence numbers rather than positions, because the log is a tail and a rewind can
     * make it shorter - an index would announce an old line every time somebody pressed undo.
     */
    public static void seen(BlockPos table, GameView board, long now) {
        if (board == null || table == null) {
            return;
        }
        LogEntry newest = null;
        for (LogEntry entry : board.log()) {
            if (!entry.undone() && ROLL_KEYS.contains(entry.key())
                    && (newest == null || entry.sequence() > newest.sequence())) {
                newest = entry;
            }
        }
        if (newest == null) {
            return;
        }
        Shown current = showing;
        if (current != null && current.table().equals(table)
                && current.entry().sequence() == newest.sequence()) {
            return;
        }
        showing = new Shown(table, newest, now);
    }

    /** The result to draw over this table right now, or empty once it has had its moment. */
    public static java.util.Optional<Shown> showingAt(BlockPos table, long now) {
        Shown current = showing;
        if (current == null || !current.table().equals(table)
                || now - current.at() > SHOWN_MILLIS) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(current);
    }

    /** How far through its moment this result is, from 0 at the roll to 1 as it goes. */
    public static float progress(Shown shown, long now) {
        return Math.max(0f, Math.min(1f, (now - shown.at()) / (float) SHOWN_MILLIS));
    }

    /** Forgotten when a board closes, so the next table does not open wearing this one's roll. */
    public static void forget() {
        showing = null;
    }
}
