package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.ChairSeat;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableSeats;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.item.GatheringContent;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chairs at a table: sitting in one takes the seat it faces, and getting up - however it happens -
 * gives the seat back, with nobody left riding an invisible seat and no seat held by nobody.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChairGameTest {

    @GameTest(template = "tables")
    public static void sittingInAChairAgainstATableTakesThatSeat(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.north(), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        Optional<SeatAnchor> seat = TableSeats.seatOf(helper.getLevel(), table, player.getUUID());
        if (seat.isEmpty() || seat.get().side() != Side.NORTH) {
            helper.fail("sitting in a chair against the north edge took " + seat);
            return;
        }
        if (!(player.getVehicle() instanceof ChairSeat)) {
            helper.fail("the player sat in the chair is riding " + player.getVehicle());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void gettingUpGivesTheSeatBack(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.north(), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        player.stopRiding();
        if (TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isPresent()) {
            helper.fail("getting up out of the chair kept the seat");
            return;
        }
        if (!helper.getLevel().getEntitiesOfClass(ChairSeat.class, new net.minecraft.world.phys.AABB(chair)).isEmpty()) {
            helper.fail("getting up left the seat entity in the chair");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void breakingTheChairStandsThemUp(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.north(), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        helper.getLevel().removeBlock(chair, false);
        if (player.isPassenger()) {
            helper.fail("the chair was broken and the player is still sitting on " + player.getVehicle());
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isPresent()) {
            helper.fail("the chair was broken and the player still holds the seat");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void leavingTheTableFromTheBoardGetsYouOutOfTheChair(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.north(), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        TableSeats.leave(helper.getLevel(), table, player.getUUID());
        if (player.isPassenger()) {
            helper.fail("a player who left the table is still sitting in its chair");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aChairAtSomebodyElsesSeatIsNotSatIn(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        UUID somebody = new UUID(4L, 2L);
        SeatAnchor north = dev.gathering.block.TableClusters.at(helper.getLevel(), table).seats().stream()
                .filter(anchor -> anchor.side() == Side.NORTH).findFirst().orElseThrow();
        TableSeats.take(helper.getLevel(), table, north.cell(), north.side(), somebody);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.north(), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        if (player.isPassenger()) {
            helper.fail("a player sat down in a chair at somebody else's seat");
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), table, somebody).isEmpty()) {
            helper.fail("somebody else's seat was taken from them by a chair");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aChairAwayFromATableIsOnlyAChair(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.offset(0, 0, -3), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        if (!(player.getVehicle() instanceof ChairSeat)) {
            helper.fail("a chair on its own could not be sat in");
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isPresent()) {
            helper.fail("a chair nowhere near an edge took a seat at the table");
            return;
        }
        player.stopRiding();
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** A chair at this absolute position, facing this way. */
    private static BlockPos chairAt(GameTestHelper helper, BlockPos where, Direction facing) {
        BlockState chair = GatheringContent.CHAIR.get().defaultBlockState().setValue(ChairBlock.FACING, facing);
        helper.getLevel().setBlock(where, chair, 3);
        return where;
    }
}
