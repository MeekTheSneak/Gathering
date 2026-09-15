package dev.gathering.server.events;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a tournament puts on a board beside its tables: the data other mods' boards read. No board
 * here, so no other mod needed - Create's display sources are tested with Create installed.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EventBoardGameTest {

    private EventBoardGameTest() {
    }

    /** Any block of an event's table reads its standings, its pairings, its round and that table's match. */
    @GameTest(template = "empty")
    public static void aTableReadsItsTournament(GameTestHelper helper) {
        BlockPos table = place(helper, 1, 2, 1);
        EventState state = fourPlayerEvent(helper, table);
        try {
            EventBoard.Board board = EventBoard.at(helper.getLevel(), table.east()).orElse(null);
            if (board == null) {
                helper.fail("a table's east block did not read the tournament at that table");
                return;
            }
            if (board.standings().size() != 4 || board.pairings().size() != 2 || board.round() != 1
                    || board.phase() != Tournament.Phase.SWISS || board.thisTable() != 1) {
                helper.fail("the board read " + board);
                return;
            }
            EventBoard.Match match = board.match().orElse(null);
            if (match == null || !match.first().startsWith("P") || !match.result().isEmpty()) {
                helper.fail("this table's match was read as " + match);
                return;
            }
            if (EventBoard.at(helper.getLevel(), table.above(3)).isPresent()) {
                helper.fail("a block that is no table read a tournament");
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** After the event, the table goes on showing how it finished, final places included. */
    @GameTest(template = "empty")
    public static void aFinishedTournamentStaysOnTheBoard(GameTestHelper helper) {
        BlockPos table = place(helper, 1, 2, 1);
        EventState state = fourPlayerEvent(helper, table);
        try {
            Tournament tournament = state.tournament();
            while (!tournament.isOver()) {
                for (Pairing pairing : tournament.currentRound().orElseThrow().pairings()) {
                    if (!pairing.isConfirmed()) {
                        tournament = tournament.settle(pairing.table(), new MatchResult(2, 0, 0));
                    }
                }
                tournament = tournament.nextRound();
            }
            Events.setForTesting(state, tournament);
            EventBoard.Board board = EventBoard.at(helper.getLevel(), table).orElse(null);
            if (board == null || board.phase() != Tournament.Phase.FINISHED || board.places().size() != 4) {
                helper.fail("a finished tournament's table read " + board);
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** For the tests of other mods' boards: the same event, from outside this package. */
    public static EventState fourPlayerEventForCompat(GameTestHelper helper, BlockPos table) {
        return fourPlayerEvent(helper, table);
    }

    /** For the tests of other mods' boards: a table, from outside this package. */
    public static BlockPos placeForCompat(GameTestHelper helper, int x, int y, int z) {
        return place(helper, x, y, z);
    }

    static EventState fourPlayerEvent(GameTestHelper helper, BlockPos table) {
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Board", new UUID(7L, 7L),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        for (int index = 0; index < 4; index++) {
            tournament = tournament.register(Entrant.registering(new UUID(9L, index), "P" + index, 1500 - index));
        }
        tournament = tournament.beginPreparing();
        for (int index = 0; index < 4; index++) {
            tournament = tournament.markReady(new UUID(9L, index));
        }
        tournament = tournament.startSwiss();
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of(table));
        Events.putForTesting(state);
        return state;
    }

    static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        var block = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), block.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
