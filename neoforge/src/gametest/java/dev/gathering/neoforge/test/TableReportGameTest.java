package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The two commands that answer "it does not work".
 * <p>Both are about a table in a running world, so neither can be checked anywhere else: the
 * report reads a block entity, a cluster and a session together, and filling a board submits
 * real move events through a real session.
 * <p>The report is checked for what it must never say as much as for what it says. Every line
 * is sent to whoever asked, which on a shared server is not necessarily a player entitled to
 * know what is in anybody's library.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableReportGameTest {

    @GameTest(template = "empty")
    public static void aReportSaysWhatIsAtTheTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = tableSeating(helper, player);
        startWithADeck(level, origin, player, 20);

        List<String> lines = TableReport.describe(level, origin);
        String said = String.join("\n", lines);
        if (!said.contains("session: running")) {
            helper.fail("a running table did not report a running session: " + said);
            return;
        }
        if (!said.contains("seat 0")) {
            helper.fail("a table with somebody at it named no seat: " + said);
            return;
        }
        if (!said.contains("library 20")) {
            helper.fail("a twenty card library was not counted: " + said);
            return;
        }
        helper.succeed();
    }

    /**
     * A report never names a card.
     * <p>The whole report goes to whoever typed the command, and on a server that is not
     * necessarily somebody entitled to read a library. Counts are public; identities are the
     * one thing the mod guards.
     */
    @GameTest(template = "empty")
    public static void aReportNamesNoCard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = tableSeating(helper, player);
        startWithADeck(level, origin, player, 12);

        for (String line : TableReport.describe(level, origin)) {
            if (line.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")) {
                helper.fail("the report printed something shaped like a card id: " + line);
                return;
            }
        }
        helper.succeed();
    }

    /** A table with nothing running says so rather than pretending. */
    @GameTest(template = "empty")
    public static void aQuietTableSaysSo(GameTestHelper helper) {
        BlockPos origin = tableSeating(helper, helper.makeMockServerPlayerInLevel());
        String said = String.join("\n", TableReport.describe(helper.getLevel(), origin));
        if (!said.contains("session: none")) {
            helper.fail("a table with no game running did not say so: " + said);
            return;
        }
        helper.succeed();
    }

    /**
     * Filling a board puts cards on it, one per spot.
     * <p>Counted at both ends: the library must lose exactly what the battlefield gains, and
     * no two cards may end up on the same place - a fill that stacked them all would leave a
     * board that looks like one card, which is the opposite of what it is for.
     */
    @GameTest(template = "empty")
    public static void fillingABoardSpreadsCardsOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = tableSeating(helper, player);
        GameSession session = startWithADeck(level, origin, player, 30);

        TableReport.Filled filled = TableReport.fill(level, origin, player, 12);
        if (!filled.worked() || filled.played() != 12) {
            helper.fail("filling a board played " + filled.played() + ": " + filled.problem());
            return;
        }
        if (session.state().count(ZoneRef.of(new SeatId(0), Zone.LIBRARY)) != 18) {
            helper.fail("a thirty card library came back as "
                    + session.state().count(ZoneRef.of(new SeatId(0), Zone.LIBRARY)));
            return;
        }
        List<CardInstanceId> onTheTable =
                session.state().contents(new SeatId(0), Zone.BATTLEFIELD);
        if (onTheTable.size() != 12) {
            helper.fail("twelve cards were played and " + onTheTable.size() + " arrived");
            return;
        }
        Set<String> spots = new HashSet<>();
        for (CardInstanceId card : onTheTable) {
            TablePosition where = session.state().requireCard(card).position();
            if (where == null) {
                helper.fail("a filled card was put down nowhere");
                return;
            }
            if (!spots.add(where.x() + "," + where.y())) {
                helper.fail("two filled cards landed on the same spot: " + where);
                return;
            }
        }
        helper.succeed();
    }

    /** Asking for more than there is plays what there is, rather than refusing. */
    @GameTest(template = "empty")
    public static void fillingTakesWhateverIsLeft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = tableSeating(helper, player);
        startWithADeck(level, origin, player, 5);

        TableReport.Filled filled = TableReport.fill(level, origin, player, 40);
        if (!filled.worked() || filled.played() != 5) {
            helper.fail("a five card library filled " + filled.played() + " spots");
            return;
        }
        helper.succeed();
    }

    /** Somebody with no seat there fills nothing, and is told why. */
    @GameTest(template = "empty")
    public static void somebodyWithNoSeatFillsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer seated = helper.makeMockServerPlayerInLevel();
        BlockPos origin = tableSeating(helper, seated);
        GameSession session = startWithADeck(level, origin, seated, 10);

        ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
        TableReport.Filled filled = TableReport.fill(level, origin, watcher, 4);
        if (filled.worked()) {
            helper.fail("somebody with no seat filled a board anyway");
            return;
        }
        if (!session.state().contents(new SeatId(0), Zone.BATTLEFIELD).isEmpty()) {
            helper.fail("a refused fill still moved cards");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ setup

    private static BlockPos tableSeating(GameTestHelper helper, ServerPlayer player) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(0);
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());
        return origin;
    }

    private static GameSession startWithADeck(
            ServerLevel level, BlockPos origin, ServerPlayer player, int librarySize) {
        TableSessions.start(level, origin, MatchRules.single(FormatPresets.COMMANDER));
        GameSession session = TableSessions.sessionAt(level, origin).orElseThrow();
        SeatId seat = new SeatId(0);
        session.submit(new GameEvent.SeatTaken(seat,
                new PlayerRef(player.getUUID(), player.getGameProfile().getName())));
        List<CardIdentity> library = new ArrayList<>(librarySize);
        for (int index = 0; index < librarySize; index++) {
            library.add(CardIdentity.ofPrinting(new UUID(7L, index), false));
        }
        session.submit(new GameEvent.DeckLoaded(seat, library, List.of()));
        return session;
    }
}
