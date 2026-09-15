package dev.gathering.neoforge.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.gathering.Gathering;
import dev.gathering.server.events.EventBoardGameTest;
import dev.gathering.server.events.EventState;
import dev.gathering.server.events.Events;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create's Display Link, placed on a table, reading its tournament. Registered only when Create is
 * installed - see PackGameTests.
 */
@PrefixGameTestTemplate(false)
public final class CreateDisplayGameTest {

    private CreateDisplayGameTest() {
    }

    /** A link on a table offers this mod's sources, and each says what the tournament there is doing. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aDisplayLinkOnATableShowsItsTournament(GameTestHelper helper) {
        BlockPos table = EventBoardGameTest.placeForCompat(helper, 1, 2, 1);
        EventState state = EventBoardGameTest.fourPlayerEventForCompat(helper, table);
        try {
            List<DisplaySource> offered = DisplaySource.getAll(helper.getLevel(), table);
            for (DisplaySource ours : List.of(CreateCompat.STANDINGS.get(), CreateCompat.PAIRINGS.get(),
                    CreateCompat.ROUND.get(), CreateCompat.TABLE_MATCH.get(), CreateCompat.TABLE_LIFE.get())) {
                if (!offered.contains(ours)) {
                    helper.fail("a Display Link on a table does not offer " + ours.getName().getString());
                    return;
                }
            }
            BlockPos linkAt = table.above();
            helper.getLevel().setBlock(linkAt,
                    AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, Direction.UP), 3);
            if (!(helper.getLevel().getBlockEntity(linkAt) instanceof DisplayLinkBlockEntity link)) {
                helper.fail("no Display Link was placed on the table");
                return;
            }
            DisplayLinkContext context = new DisplayLinkContext(helper.getLevel(), link);
            if (!context.getSourcePos().equals(table)) {
                helper.fail("the Display Link reads " + context.getSourcePos() + ", not the table at " + table);
                return;
            }
            DisplayTargetStats board = new DisplayTargetStats(10, 40, null);
            String standings = said(CreateCompat.STANDINGS.get().provideText(context, board));
            String pairings = said(CreateCompat.PAIRINGS.get().provideText(context, board));
            String round = said(CreateCompat.ROUND.get().provideText(context, board));
            String match = said(CreateCompat.TABLE_MATCH.get().provideText(context, board));
            if (!standings.contains("P0") || !standings.contains("P3") || !pairings.contains("P0")
                    || !round.contains("display.gathering.round.swiss") || !match.contains("display.gathering.pairing")) {
                helper.fail("the boards said: standings [" + standings + "] pairings [" + pairings + "] round [" + round
                        + "] match [" + match + "]");
                return;
            }
            String life = said(CreateCompat.TABLE_LIFE.get().provideText(context, board));
            if (!life.contains("display.gathering.no_game")) {
                helper.fail("a table with no game on it showed life totals: " + life);
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** The text as written, keys and arguments both, so a check can find a name inside a translation. */
    private static String said(List<MutableComponent> lines) {
        return lines.stream().map(line -> line.getString() + " " + line.toString()).collect(Collectors.joining(" | "));
    }
}
