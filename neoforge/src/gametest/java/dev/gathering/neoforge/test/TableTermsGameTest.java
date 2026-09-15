package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.TableTerms;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.server.TableBroadcast;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a table tells everybody who can see its board it is playing.
 * <p>Read from the server's own record - the match it started, whether a format was chosen, whether
 * it is for keeps - since the board says it to somebody deciding whether to sit down, and one of
 * those things decides whether they can lose a card.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableTermsGameTest {

    @GameTest(template = "tables")
    public static void aChosenFormatSaysItsNameAndLength(GameTestHelper helper) {
        BlockPos origin = seated(helper, 1);
        TableBlock.entityAt(helper.getLevel(), origin).ifPresent(table -> table.formatWasChosen(true));
        TableSessions.start(helper.getLevel(), origin, new MatchRules(FormatPresets.MODERN, 3));
        TableTerms terms = TableBroadcast.termsAt(helper.getLevel(), origin);
        if (!terms.formatId().equals(FormatPresets.MODERN.id()) || terms.bestOf() != 3 || terms.gameNumber() != 1) {
            helper.fail("a Modern best of three said " + terms);
            return;
        }
        if (!terms.notes().isEmpty()) {
            helper.fail("the usual terms were marked " + terms.notes());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aGameWithNoFormatChosenIsFreePlay(GameTestHelper helper) {
        BlockPos origin = seated(helper, 1);
        TableSessions.start(helper.getLevel(), origin, MatchRules.single(FormatPresets.COMMANDER));
        TableTerms terms = TableBroadcast.termsAt(helper.getLevel(), origin);
        if (!terms.freePlay() || !terms.notes().contains(TableTerms.Note.FREE_PLAY)) {
            helper.fail("a game nobody chose a format for said " + terms);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aTablePlayedForKeepsSaysSoFirst(GameTestHelper helper) {
        BlockPos origin = seated(helper, 1);
        TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
        table.formatWasChosen(true);
        table.playForKeeps(true);
        TableSessions.start(helper.getLevel(), origin, MatchRules.single(FormatPresets.COMMANDER));
        TableTerms terms = TableBroadcast.termsAt(helper.getLevel(), origin);
        if (!terms.forKeeps() || terms.notes().isEmpty() || terms.notes().get(0) != TableTerms.Note.FOR_KEEPS) {
            helper.fail("a table played for keeps said " + terms);
            return;
        }
        helper.succeed();
    }

    private static BlockPos seated(GameTestHelper helper, int x) {
        BlockPos origin = TestTables.place(helper, x, 2, 1);
        List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
        SeatAnchor anchor = anchors.get(0);
        TableSeats.take(helper.getLevel(), origin, anchor.cell(), anchor.side(), new java.util.UUID(12L, x));
        return origin;
    }
}
