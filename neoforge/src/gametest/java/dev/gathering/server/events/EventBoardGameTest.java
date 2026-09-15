package dev.gathering.server.events;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Tournament;
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

    /**
     * A tournament's table carried somewhere - its data loaded where it went and the old table cleared
     * in the same tick, as any mover does it - is still that tournament's table, with its number.
     */
    @GameTest(template = "empty")
    public static void aCarriedTableStaysInItsTournament(GameTestHelper helper) {
        BlockPos table = place(helper, 1, 2, 1);
        EventState state = fourPlayerEvent(helper, table);
        try {
            var saved = TableBlock.entityAt(helper.getLevel(), table).orElseThrow()
                    .saveWithFullMetadata(helper.getLevel().registryAccess());
            BlockPos there = place(helper, 7, 2, 7);
            TableBlock.entityAt(helper.getLevel(), there).orElseThrow().loadWithComponents(saved, helper.getLevel().registryAccess());
            for (TablePart part : TablePart.values()) {
                helper.getLevel().setBlock(part.offsetFrom(table), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            if (!state.tables.equals(List.of(there))) {
                helper.fail("the tournament lists its table at " + state.tables + " after it was carried to " + there);
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /**
     * The same for a long table - two joined, both numbered - carried with the second table's blocks
     * cleared first: each keeps its own number where it went, rather than both becoming the first.
     */
    @GameTest(template = "empty")
    public static void aCarriedLongTableKeepsBothNumbers(GameTestHelper helper) {
        BlockPos first = place(helper, 1, 2, 1);
        BlockPos second = place(helper, 4, 2, 1);
        var tournament = Tournament.create(UUID.randomUUID(), "Long", new UUID(7L, 8L),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of(first, second));
        Events.putForTesting(state);
        try {
            // Both tables written down and loaded where they went, as a mover does with every block entity.
            var firstSaved = helper.getLevel().getBlockEntity(first).saveWithFullMetadata(helper.getLevel().registryAccess());
            var secondSaved = helper.getLevel().getBlockEntity(second).saveWithFullMetadata(helper.getLevel().registryAccess());
            BlockPos firstThere = place(helper, 1, 2, 10);
            BlockPos secondThere = place(helper, 4, 2, 10);
            helper.getLevel().getBlockEntity(firstThere).loadWithComponents(firstSaved, helper.getLevel().registryAccess());
            helper.getLevel().getBlockEntity(secondThere).loadWithComponents(secondSaved, helper.getLevel().registryAccess());
            // The second table's corners before its origin, while the first still stands - so the second's
            // blocks find the long table's game kept on the first, not on themselves.
            for (BlockPos origin : List.of(second, first)) {
                List<TablePart> parts = new java.util.ArrayList<>(List.of(TablePart.values()));
                java.util.Collections.reverse(parts);
                for (TablePart part : parts) {
                    helper.getLevel().setBlock(part.offsetFrom(origin), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
            if (!state.tables.equals(List.of(firstThere, secondThere))) {
                helper.fail("a long table carried off is listed at " + state.tables + ", not " + List.of(firstThere, secondThere));
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
        return dev.gathering.neoforge.test.TestTables.place(helper, x, y, z);
    }
}
