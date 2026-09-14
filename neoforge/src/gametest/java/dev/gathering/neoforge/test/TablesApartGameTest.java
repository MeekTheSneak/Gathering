package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.PodSignups;
import dev.gathering.server.TablesApart;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A long table played as one surface or as several tables side by side.
 * <p>What matters is that apart means apart everywhere the mod asks: each table its own seats,
 * its own game, and nothing on one reaching the other - and that the shape cannot change under
 * anything that is using it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TablesApartGameTest {

    /** Three tables in a line, split: three games at once, each with its own two seats. */
    @GameTest(template = "tables")
    public static void alongTablePlayedApartRunsAGameAtEachTable(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        BlockPos second = place(helper, 3, 2, 1);
        BlockPos third = place(helper, 5, 2, 1);
        if (TableClusters.at(helper.getLevel(), first).cells().size() != 3) {
            helper.fail("three tables in a line are not one cluster to begin with");
            return;
        }
        if (TablesApart.set(helper.getLevel(), second, true) != TablesApart.Result.DONE) {
            helper.fail("the long table would not be played apart");
            return;
        }
        for (BlockPos table : new BlockPos[] {first, second, third}) {
            if (TableClusters.at(helper.getLevel(), table).cells().size() != 1
                    || TableClusters.at(helper.getLevel(), table).seats().size() != 2) {
                helper.fail("a table played apart is not a table of its own at " + table);
                return;
            }
            TableSeats.take(helper.getLevel(), table, new TableCell(0, 0), Side.NORTH, UUID.randomUUID());
            TableSeats.take(helper.getLevel(), table, new TableCell(0, 0), Side.SOUTH, UUID.randomUUID());
        }
        for (BlockPos table : new BlockPos[] {first, second, third}) {
            TableSessions.Outcome outcome = TableSessions.start(helper.getLevel(), table,
                    MatchRules.single(FormatPresets.COMMANDER));
            if (outcome != TableSessions.Outcome.STARTED) {
                helper.fail("a game would not start at a table played apart: " + outcome);
                return;
            }
        }
        for (BlockPos table : new BlockPos[] {first, second, third}) {
            var session = TableSessions.sessionAt(helper.getLevel(), table).orElse(null);
            if (session == null || session.state().seats().size() != 2) {
                helper.fail("the game at " + table + " is not a two-seat game of its own");
                return;
            }
            if (PodSignups.seatedAt(helper.getLevel(), table).size() != 2) {
                helper.fail("the table at " + table + " counts players from its neighbors");
                return;
            }
        }
        if (TableSessions.sessionAt(helper.getLevel(), first).orElseThrow()
                == TableSessions.sessionAt(helper.getLevel(), third).orElseThrow()) {
            helper.fail("two tables played apart share one game");
            return;
        }
        helper.succeed();
    }

    /** Nothing changes shape under a game, and a table alone has nothing to split. */
    @GameTest(template = "tables")
    public static void theShapeDoesNotChangeUnderAGame(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        BlockPos second = place(helper, 3, 2, 1);
        TableSeats.take(helper.getLevel(), first, new TableCell(0, 0), Side.NORTH, UUID.randomUUID());
        TableSessions.start(helper.getLevel(), first, MatchRules.single(FormatPresets.COMMANDER));

        if (TablesApart.set(helper.getLevel(), second, true) != TablesApart.Result.IN_USE) {
            helper.fail("a long table was split under a game running on it");
            return;
        }
        if (TableClusters.at(helper.getLevel(), second).cells().size() != 2) {
            helper.fail("a refused split still split the tables");
            return;
        }
        BlockPos alone = place(helper, 9, 2, 1);
        if (TablesApart.set(helper.getLevel(), alone, true) != TablesApart.Result.ALONE) {
            helper.fail("a table on its own was offered a split");
            return;
        }
        helper.succeed();
    }

    /** Played apart and back together is one surface again, and the setting survives a restart. */
    @GameTest(template = "tables")
    public static void backTogetherIsOneTableAndApartSurvivesARestart(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        BlockPos second = place(helper, 3, 2, 1);
        TablesApart.set(helper.getLevel(), first, true);

        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), first).orElseThrow();
        var registries = helper.getLevel().registryAccess();
        TableBlockEntity reloaded = new TableBlockEntity(first, helper.getLevel().getBlockState(first));
        reloaded.loadWithComponents(table.saveWithoutMetadata(registries), registries);
        if (!reloaded.playsApart()) {
            helper.fail("playing apart did not survive a save");
            return;
        }
        if (TablesApart.set(helper.getLevel(), second, false) != TablesApart.Result.DONE
                || TableClusters.at(helper.getLevel(), first).cells().size() != 2) {
            helper.fail("the tables did not go back to being one");
            return;
        }
        helper.succeed();
    }

    /** A table pushed onto a long table played apart is a table of its own until it is set. */
    @GameTest(template = "tables")
    public static void aTableAddedBesideTablesPlayedApartStandsAlone(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        TablesApart.set(helper.getLevel(), first, true);
        BlockPos added = place(helper, 5, 2, 1);
        if (TableClusters.at(helper.getLevel(), added).cells().size() != 1) {
            helper.fail("a table added beside tables played apart joined one of them");
            return;
        }
        if (TablesApart.set(helper.getLevel(), added, true) != TablesApart.Result.DONE
                || TablesApart.tablesTouching(helper.getLevel(), added).stream().anyMatch(t -> !t.playsApart())) {
            helper.fail("setting the added table did not set the whole long table");
            return;
        }
        helper.succeed();
    }

    /** Somebody walking past cannot rearrange a long table; somebody sitting at it can. */
    @GameTest(template = "tables")
    public static void onlySomebodySeatedChangesHowTablesArePlayed(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        var passerBy = helper.makeMockServerPlayerInLevel();
        passerBy.setPos(first.getX() + 1.0, first.getY(), first.getZ() + 1.0);
        TablesApart.handle(passerBy, new dev.gathering.network.TablesApartPayload(first, true));
        if (TableBlock.entityAt(helper.getLevel(), first).orElseThrow().playsApart()) {
            helper.fail("a player not sitting at the tables split them");
            return;
        }
        TableSeats.take(helper.getLevel(), first, new TableCell(0, 0), Side.NORTH, passerBy.getUUID());
        TablesApart.handle(passerBy, new dev.gathering.network.TablesApartPayload(first, true));
        if (!TableBlock.entityAt(helper.getLevel(), first).orElseThrow().playsApart()) {
            helper.fail("a player sitting at the tables could not split them");
            return;
        }
        helper.succeed();
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
