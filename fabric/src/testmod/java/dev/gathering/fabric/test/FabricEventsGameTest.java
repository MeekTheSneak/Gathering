package dev.gathering.fabric.test;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.PodSignups;
import dev.gathering.server.TablesApart;
import dev.gathering.server.events.EventState;
import dev.gathering.server.events.Events;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Tournament;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Events and long tables on this loader.
 * <p>The rules are shared and tested at length on NeoForge. What is checked here is that the
 * same world behavior holds under Fabric's own block entity and player handling: three games at
 * once on a long table played apart, and a pack held by a sign-up coming back to its owner.
 */
public class FabricEventsGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void alongTablePlayedApartRunsThreeGames(GameTestHelper helper) {
        BlockPos[] tables = {place(helper, 0), place(helper, 3), place(helper, 6)};
        if (TablesApart.set(helper.getLevel(), tables[1], true) != TablesApart.Result.DONE) {
            helper.fail("the long table would not be played apart");
            return;
        }
        for (BlockPos table : tables) {
            TableSeats.take(helper.getLevel(), table, new TableCell(0, 0), Side.NORTH, UUID.randomUUID());
            TableSeats.take(helper.getLevel(), table, new TableCell(0, 0), Side.SOUTH, UUID.randomUUID());
            if (TableClusters.at(helper.getLevel(), table).cells().size() != 1
                    || TableSessions.start(helper.getLevel(), table, MatchRules.single(FormatPresets.COMMANDER))
                            != TableSessions.Outcome.STARTED) {
                helper.fail("a table played apart did not run its own game at " + table);
                return;
            }
        }
        helper.succeed();
    }

    /** A tournament round seats each pair at its numbered table and starts its match. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void atournamentRoundSeatsEachPairAtItsTable(GameTestHelper helper) {
        java.util.List<BlockPos> tables = java.util.List.of(place(helper, 0), place(helper, 4));
        java.util.List<net.minecraft.server.level.ServerPlayer> players = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            players.add(player);
        }
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Fabric", players.get(0).getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        for (int index = 0; index < 4; index++) {
            tournament = tournament.register(Entrant.registering(players.get(index).getUUID(), "F" + index, 1500 - index));
        }
        tournament = tournament.beginPreparing();
        for (var player : players) {
            tournament = tournament.markReady(player.getUUID());
        }
        EventState state = Events.stateForTesting(tournament.startSwiss(), helper.getLevel(), tables);
        Events.putForTesting(state);
        Events.seatRoundForTesting(helper.getLevel().getServer(), state);
        for (Pairing pairing : state.tournament().currentRound().orElseThrow().pairings()) {
            BlockPos table = state.table(pairing.table()).orElseThrow();
            if (TableSeats.seatOf(helper.getLevel(), table, pairing.a()).isEmpty()
                    || TableSeats.seatOf(helper.getLevel(), table, pairing.b()).isEmpty()
                    || TableSessions.sessionAt(helper.getLevel(), table).isEmpty()) {
                helper.fail("table " + pairing.table() + " did not seat its pairing and start their match");
                return;
            }
        }
        Events.removeForTesting(state);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apackHeldForAnEventComesBack(GameTestHelper helper) {
        BlockPos table = place(helper, 0);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(table.getX() + 1.0, table.getY(), table.getZ() + 1.0);
        TableSeats.take(helper.getLevel(), table, new TableCell(0, 0), Side.NORTH, player.getUUID());
        PodSignups.create(helper.getLevel(), table, player.getUUID(), PodSettings.usual(PodSettings.Kind.SEALED));
        ItemStack hand = PackItem.of(new PackComponent("m21", "draft"));
        hand.setCount(2);
        if (PodSignups.putIn(player, table, hand) != 2 || !hand.isEmpty()) {
            helper.fail("the packs did not go in");
            return;
        }
        TableSeats.leave(helper.getLevel(), table, player.getUUID());
        PodSignups.seatReleased(helper.getLevel(), table, player.getUUID());
        int back = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).has(dev.gathering.registry.GatheringComponents.PACK.get())) {
                back += player.getInventory().getItem(slot).getCount();
            }
        }
        if (back != 2) {
            helper.fail("standing up handed back " + back + " of 2 packs");
            return;
        }
        helper.succeed();
    }

    private static BlockPos place(GameTestHelper helper, int x) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, 1, 0));
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
