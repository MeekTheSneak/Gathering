package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.SeatState;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.table.TableCluster;
import dev.gathering.core.ui.TableSpread;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * What the server thinks is happening at a table, and a way to fill one up.
 *
 * <p>Both halves of "it does not work". The first is a straight answer to what the server
 * believes - who is in which seat, whether a session is running, what is in each zone - which
 * until now could only be got at by reading a save file. The second is a board with a real
 * game's worth of cards on it in one command, because "the cards render too small on a
 * crowded table" is not a thing anybody should have to play forty cards by hand to see.
 *
 * <p><b>Nothing here names a card.</b> The report is counts, seats and names, all of which
 * every client at the table already holds; filling a board moves cards out of the caller's own
 * library and no one else's, which is a thing they could do by hand one card at a time.
 */
public final class TableReport {

    /** How far a player can be from the table they are asking about. Their own reach. */
    public static final double REACH = 6.0;

    /** The most a single fill will play, so a mistyped number cannot bury a mat. */
    public static final int MOST_AT_ONCE = 120;

    private TableReport() {
    }

    /** The table this player is looking at, or empty. */
    public static Optional<BlockPos> lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0.0f, false);
        if (!(hit instanceof BlockHitResult block)) {
            return Optional.empty();
        }
        Level level = player.level();
        BlockState state = level.getBlockState(block.getBlockPos());
        if (!(state.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        return Optional.of(TableBlock.originOf(state, block.getBlockPos()));
    }

    /**
     * Everything public about a table, one fact to a line.
     *
     * <p>Plain lines rather than a formatted report: this is read in a chat box by somebody
     * comparing it against what they can see, and the useful shape for that is short lines
     * that can be scanned down the left.
     */
    public static List<String> describe(ServerLevel level, BlockPos origin) {
        List<String> lines = new ArrayList<>();
        TableCluster cluster = TableClusters.at(level, origin);
        lines.add("table at " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ());
        lines.add("  cluster: " + cluster.tableCount() + " table(s), "
                + cluster.capacity() + " seat(s)");

        TableBlockEntity table = TableBlock.entityAt(level, origin).orElse(null);
        if (table == null) {
            lines.add("  no table entity here");
            return lines;
        }
        lines.add("  felt: " + table.felt().map(dye -> dye.getName()).orElse("undyed"));
        lines.add("  command zone: " + yesNo(table.hasCommandZone())
                + ", format chosen: " + yesNo(table.formatWasChosen()));

        lines.add("  chairs taken: " + TableSeats.occupiedSeats(level, origin)
                + " of " + cluster.capacity());

        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        if (session == null) {
            lines.add("  session: none");
            return lines;
        }
        GameState state = session.state();
        lines.add("  session: " + (state.ended() ? "ended" : "running")
                + ", undo " + session.undoMode().name().toLowerCase(Locale.ROOT)
                + ", " + session.records().size() + " action(s)");
        lines.add("  turn " + state.turn().turnNumber() + ": seat "
                + state.turn().activeSeat().index()
                + ", " + state.turn().phase().name().toLowerCase(Locale.ROOT));
        table.match().ifPresent(match -> lines.add("  match: " + describeMatch(match)));
        if (table.playingForKeeps() || table.pot().size() > 0) {
            lines.add("  ante: " + yesNo(table.playingForKeeps())
                    + ", pot holds " + table.pot().size());
        }
        for (SeatId seat : state.seats()) {
            lines.add("  " + describeSeat(state, seat));
        }
        return lines;
    }

    private static String describeMatch(MatchState match) {
        StringBuilder said = new StringBuilder("game ").append(match.gameNumber());
        said.append(match.isDecided() ? ", decided" : ", undecided");
        match.winner().ifPresent(winner -> said.append(", seat ")
                .append(winner.index()).append(" leads"));
        return said.toString();
    }

    private static String describeSeat(GameState state, SeatId seat) {
        SeatState sat = state.seatState(seat);
        StringBuilder said = new StringBuilder("seat ").append(seat.index()).append(" ");
        said.append(sat.whoseBoard().map(who -> who.name()).orElse("(empty)"));
        said.append(sat.isOccupied() ? "" : " (away)");
        said.append(" life ").append(sat.life());
        for (Zone zone : Zone.values()) {
            int held = state.count(ZoneRef.of(seat, zone));
            if (held > 0) {
                said.append(" ").append(zone.name().toLowerCase(Locale.ROOT)).append(" ").append(held);
            }
        }
        return said.toString();
    }

    private static String yesNo(boolean answer) {
        return answer ? "yes" : "no";
    }

    /** What came of asking a board to fill itself up. */
    public record Filled(int played, String problem) {

        public boolean worked() {
            return problem == null;
        }

        static Filled no(String why) {
            return new Filled(0, why);
        }
    }

    /**
     * Plays cards off the top of the caller's own library onto their own side of the table.
     *
     * <p>One move event each, exactly as playing them by hand would send, so the log reads the
     * same and undo walks back through them the same. Laid out on a grid rather than dropped
     * on one spot, because a stack of forty is one card as far as looking at the board goes.
     */
    public static Filled fill(ServerLevel level, BlockPos origin, ServerPlayer player, int howMany) {
        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        if (session == null) {
            return Filled.no("No game is running at that table.");
        }
        SeatId seat = TableSessions.seatIdOf(level, origin, player.getUUID()).orElse(null);
        if (seat == null) {
            return Filled.no("You are not sitting at that table.");
        }
        int wanted = Math.max(0, Math.min(MOST_AT_ONCE, howMany));
        int available = session.state().count(ZoneRef.of(seat, Zone.LIBRARY));
        int playing = Math.min(wanted, available);
        if (playing == 0) {
            return Filled.no(available == 0
                    ? "That library is empty."
                    : "Nothing to play.");
        }

        List<TablePosition> spots = TableSpread.positions(playing);
        for (int index = 0; index < playing; index++) {
            var top = session.state().topOf(ZoneRef.of(seat, Zone.LIBRARY));
            if (top.isEmpty()) {
                break;
            }
            session.submit(new GameEvent.CardMoved(seat, top.get(),
                    ZoneRef.of(seat, Zone.BATTLEFIELD), Placement.at(spots.get(index))));
        }
        TableSessions.markDirty(level, origin);
        TableBroadcast.sendToTable(level, origin);
        return new Filled(playing, null);
    }
}
