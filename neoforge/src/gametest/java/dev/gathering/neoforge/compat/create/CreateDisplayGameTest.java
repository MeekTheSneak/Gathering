package dev.gathering.neoforge.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.gathering.Gathering;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.events.EventBoardGameTest;
import dev.gathering.server.events.EventState;
import dev.gathering.server.events.Events;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create's Display Link, placed on a table reading its match, and on a Scorekeeper's Desk reading its tournament. Registered only when Create is
 * installed - see PackGameTests.
 */
@PrefixGameTestTemplate(false)
public final class CreateDisplayGameTest {

    private CreateDisplayGameTest() {
    }

    /** A link on a table offers what is the table's own - its match and its game - and each says so. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aDisplayLinkOnATableShowsItsMatch(GameTestHelper helper) {
        BlockPos table = EventBoardGameTest.placeForCompat(helper, 1, 2, 1);
        EventState state = EventBoardGameTest.fourPlayerEventForCompat(helper, table);
        try {
            List<DisplaySource> offered = DisplaySource.getAll(helper.getLevel(), table);
            if (!offered.contains(CreateCompat.TABLE_MATCH.get()) || !offered.contains(CreateCompat.TABLE_LIFE.get())) {
                helper.fail("a Display Link on a table offers " + offered);
                return;
            }
            if (offered.contains(CreateCompat.TOURNAMENT.get())) {
                helper.fail("a table offers the whole tournament, which is the desk's to show");
                return;
            }
            DisplayLinkContext context = linkOn(helper, table);
            if (context == null) {
                return;
            }
            DisplayTargetStats board = new DisplayTargetStats(10, 40, null);
            String match = said(CreateCompat.TABLE_MATCH.get().provideText(context, board));
            if (!match.contains("display.gathering.pairing")) {
                helper.fail("the table's match said [" + match + "]");
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

    /**
     * A link on a Scorekeeper's Desk offers its tournament, and shows whichever part the link is set
     * to: every choice the setting offers says something of its own.
     */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aDisplayLinkOnADeskShowsWhatItIsSetTo(GameTestHelper helper) {
        BlockPos table = EventBoardGameTest.placeForCompat(helper, 1, 2, 1);
        EventState state = EventBoardGameTest.fourPlayerEventForCompat(helper, table);
        BlockPos desk = helper.absolutePos(new BlockPos(5, 2, 1));
        helper.getLevel().setBlock(desk, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState(), 3);
        try {
            if (!DisplaySource.getAll(helper.getLevel(), desk).contains(CreateCompat.TOURNAMENT.get())) {
                helper.fail("a Display Link on a desk does not offer the tournament");
                return;
            }
            DisplayLinkContext context = linkOn(helper, desk);
            if (context == null) {
                return;
            }
            DisplayTargetStats board = new DisplayTargetStats(10, 40, null);
            DisplaySource source = CreateCompat.TOURNAMENT.get();
            if (!said(source.provideText(context, board)).contains("display.gathering.desk_runs_nothing")) {
                helper.fail("a desk running nothing showed " + said(source.provideText(context, board)));
                return;
            }
            if (helper.getLevel().getBlockEntity(desk) instanceof ScorekeepersDeskBlockEntity entity) {
                entity.runs(state.tournament().id());
            }
            Map<TournamentDisplaySource.Show, String> wanted = Map.of(
                    TournamentDisplaySource.Show.STANDINGS, "P3",
                    TournamentDisplaySource.Show.PAIRINGS, "display.gathering.pairing",
                    TournamentDisplaySource.Show.ROUND, "display.gathering.round.swiss",
                    TournamentDisplaySource.Show.PLACES, "display.gathering.not_finished",
                    TournamentDisplaySource.Show.PRIZES, "display.gathering.no_prizes",
                    TournamentDisplaySource.Show.SIGNED_UP, "display.gathering.signed_up");
            for (TournamentDisplaySource.Show show : TournamentDisplaySource.Show.values()) {
                context.sourceConfig().putInt(TournamentDisplaySource.SHOW, show.ordinal());
                String text = said(source.provideText(context, board));
                if (!text.contains(wanted.get(show))) {
                    helper.fail("set to " + show + ", the desk's board said [" + text + "]");
                    return;
                }
            }
            // The rows a board has are the rows it gets: a two-row sign shows the top two standings.
            context.sourceConfig().putInt(TournamentDisplaySource.SHOW, TournamentDisplaySource.Show.STANDINGS.ordinal());
            if (source.provideText(context, new DisplayTargetStats(2, 20, null)).size() != 2) {
                helper.fail("a two-row target was given " + source.provideText(context, new DisplayTargetStats(2, 20, null)));
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    private static DisplayLinkContext linkOn(GameTestHelper helper, BlockPos source) {
        BlockPos linkAt = source.above();
        helper.getLevel().setBlock(linkAt,
                AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, Direction.UP), 3);
        if (!(helper.getLevel().getBlockEntity(linkAt) instanceof DisplayLinkBlockEntity link)) {
            helper.fail("no Display Link was placed on " + source);
            return null;
        }
        DisplayLinkContext context = new DisplayLinkContext(helper.getLevel(), link);
        if (!context.getSourcePos().equals(source)) {
            helper.fail("the Display Link reads " + context.getSourcePos() + ", not " + source);
            return null;
        }
        return context;
    }

    /** The text as written, keys and arguments both, so a check can find a name inside a translation. */
    private static String said(List<MutableComponent> lines) {
        return lines.stream().map(line -> line.getString() + " " + line.toString()).collect(Collectors.joining(" | "));
    }
}
