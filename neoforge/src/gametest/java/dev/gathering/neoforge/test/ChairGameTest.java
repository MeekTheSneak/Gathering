package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.ChairSeat;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
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
import net.minecraft.world.item.ItemStack;
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
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
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

    /**
     * A table on its own is played across whichever pair of edges its first sitter chose, and after
     * that only the edge opposite them seats anybody. The owner's rule for chairs.
     */
    @GameTest(template = "tables")
    public static void aTableOnItsOwnTurnsToItsFirstSitter(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer first = player(helper);
        BlockPos east = chairAt(helper, table.offset(3, 0, 1), Direction.WEST);
        Chairs.sit(first, east, helper.getLevel().getBlockState(east));
        Optional<SeatAnchor> firstSeat = TableSeats.seatOf(helper.getLevel(), table, first.getUUID());
        if (firstSeat.isEmpty() || firstSeat.get().side() != Side.EAST || !(first.getVehicle() instanceof ChairSeat)) {
            helper.fail("sitting in the chair at the east edge of an empty table took " + firstSeat);
            return;
        }
        ServerPlayer second = player(helper);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        Chairs.sit(second, north, helper.getLevel().getBlockState(north));
        // The north edge seats nobody now: its chair watches.
        if (TableSeats.seatOf(helper.getLevel(), table, second.getUUID()).isPresent()) {
            helper.fail("a second player took a seat at the north edge of a table played east to west");
            return;
        }
        second.stopRiding();
        BlockPos west = chairAt(helper, table.offset(-1, 0, 1), Direction.EAST);
        Chairs.sit(second, west, helper.getLevel().getBlockState(west));
        Optional<SeatAnchor> secondSeat = TableSeats.seatOf(helper.getLevel(), table, second.getUUID());
        if (secondSeat.isEmpty() || secondSeat.get().side() != Side.WEST) {
            helper.fail("the chair opposite the first player took " + secondSeat);
            return;
        }
        helper.succeed();
    }

    /**
     * A chair at a table's edge but not at its middle takes no seat, and the table stays the way it was: the
     * player sits in it and watches. It refused them once; the owner asked for chairs away from the seats to
     * watch the game.
     */
    @GameTest(template = "tables")
    public static void aChairOffTheMiddleOfAnEdgeWatches(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.offset(0, 0, -1), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        if (TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isPresent()) {
            helper.fail("a chair at the corner of a table's north edge seated the player");
            return;
        }
        if (!(player.getVehicle() instanceof ChairSeat seat) || !table.equals(seat.watchingAt())) {
            helper.fail("a chair at the corner of a table's north edge did not seat a watcher");
            return;
        }
        player.stopRiding();
        helper.succeed();
    }

    /** Right-clicking a table never seats anybody, and a deck put on a table with no game starts none. */
    @GameTest(template = "tables")
    public static void aDeckOnATableStartsNoGame(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer walkingUp = player(helper);
        use(helper, table, walkingUp, aDeck());
        if (TableSeats.seatOf(helper.getLevel(), table, walkingUp.getUUID()).isPresent()
                || TableSessions.hasSession(helper.getLevel(), table)) {
            helper.fail("right-clicking a table with a deck seated the player or started a game");
            return;
        }
        ServerPlayer sitting = player(helper);
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        Chairs.sit(sitting, chair, helper.getLevel().getBlockState(chair));
        ItemStack deck = aDeck();
        use(helper, table, sitting, deck);
        if (TableSessions.hasSession(helper.getLevel(), table)) {
            helper.fail("a deck put on a table somebody sits at, with no game chosen, started one");
            return;
        }
        if (sitting.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND) != deck || deck.isEmpty()) {
            helper.fail("a deck put on a table with no game left the player's hand");
            return;
        }
        helper.succeed();
    }

    private static ItemStack aDeck() {
        return dev.gathering.item.DeckItem.of(new dev.gathering.item.DeckComponent(
                "Deck", "", Optional.empty(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                Optional.empty(), dev.gathering.core.card.Sleeve.DEFAULT));
    }

    /** A right-click on the top of this table with this in the main hand, the way a player makes one. */
    private static void use(GameTestHelper helper, BlockPos table, ServerPlayer player, ItemStack held) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        BlockPos middle = table.offset(1, 0, 1);
        helper.getLevel().getBlockState(middle).useItemOn(held, helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(middle),
                        Direction.UP, middle, false));
    }

    /**
     * A chair taken away some other way than by a player's hand - a machine, an explosion - gets its sitter
     * up, and their cards stay theirs: somebody else sitting down there is refused, never shown the hand,
     * and the player who left sits back down to the same board.
     */
    @GameTest(template = "tables")
    public static void aBoardWaitsForItsOwnerWhenTheirChairGoes(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer owner = player(helper);
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        Chairs.sit(owner, chair, helper.getLevel().getBlockState(chair));
        if (TableSessions.start(helper.getLevel(), table, TableSessions.defaultRules()) != TableSessions.Outcome.STARTED) {
            helper.fail("fixture: the game did not start");
            return;
        }
        var session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        var seat = TableSessions.seatIdOf(helper.getLevel(), table, owner.getUUID()).orElseThrow();
        session.submit(new dev.gathering.core.game.event.GameEvent.DeckLoaded(seat,
                java.util.List.of(dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(9L, 9L), false)), java.util.List.of()));
        session.submit(new dev.gathering.core.game.event.GameEvent.CardsDrawn(seat, seat, 1));

        helper.getLevel().removeBlock(chair, false);
        if (owner.isPassenger()) {
            helper.fail("fixture: the chair went and the owner is still sitting");
            return;
        }
        chairAt(helper, chair, Direction.SOUTH);
        ServerPlayer somebodyElse = player(helper);
        Chairs.sit(somebodyElse, chair, helper.getLevel().getBlockState(chair));
        if (somebodyElse.isPassenger() || TableSeats.seatOf(helper.getLevel(), table, somebodyElse.getUUID()).isPresent()
                || TableSessions.seatIdOf(helper.getLevel(), table, somebodyElse.getUUID()).isPresent()) {
            helper.fail("another player sat down at a seat whose cards are somebody else's");
            return;
        }
        Chairs.sit(owner, chair, helper.getLevel().getBlockState(chair));
        if (!TableSessions.seatIdOf(helper.getLevel(), table, owner.getUUID()).equals(java.util.Optional.of(seat))) {
            helper.fail("the owner sat back down and did not get their own board back");
            return;
        }
        helper.succeed();
    }

    /** Sat in, a player's thighs lie on the seat: not sunk into the chair, not floating over it. */
    @GameTest(template = "tables")
    public static void aSitterSitsOnTheSeat(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        if (!(player.getVehicle() instanceof ChairSeat seat)) {
            helper.fail("the player sat in the chair is riding " + player.getVehicle());
            return;
        }
        seat.positionRider(player);
        double thighs = player.getY() + ChairSeat.HIP_HEIGHT - ChairSeat.HALF_A_THIGH;
        double onTheSeat = chair.getY() + ChairBlock.SEAT_HEIGHT;
        if (Math.abs(thighs - onTheSeat) > 1.0 / 32) {
            helper.fail("a sitter's thighs are at " + String.format("%.3f", thighs - chair.getY())
                    + " of the chair's block and its seat is at " + ChairBlock.SEAT_HEIGHT);
            return;
        }
        if (!seat.chair().equals(chair)) {
            helper.fail("the seat in the chair at " + chair + " thinks it is in " + seat.chair());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void gettingUpGivesTheSeatBack(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer player = player(helper);
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
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
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
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
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
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
        BlockPos chair = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
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
        BlockPos chair = chairAt(helper, table.offset(1, 0, -3), Direction.SOUTH);
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
