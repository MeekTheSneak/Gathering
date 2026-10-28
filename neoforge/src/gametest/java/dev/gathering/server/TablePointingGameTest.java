package dev.gathering.server;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.TablePointPayload;
import dev.gathering.network.TablePointingPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Where a seated player is pointing, and who is allowed to say so.
 * <p>A pointer moves no card and reaches no log, so the temptation is to pass it on without
 * asking anything. What that would be is a payload letting any client put any other client's arm
 * anywhere on any table in the world - so the seat is checked, the point has to be on that
 * table's own felt, and both refusals are guarded here.
 * <p>A stand-in player cannot take a payload at all: {@code Sending.to} refuses one, deliberately
 * and for good reason. So these read {@code TablePointing.watchForTesting}, one step before the
 * wire, the way the board tests read {@code TableBroadcast.watchForTesting}.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TablePointingGameTest {

    /**
     * Somewhere on the felt of a single table, comfortably inside it.
     * <p>In surface units, which are ten thousand to a table - not blocks and not pixels. The
     * first version of this test used nine thousand as its <em>off</em>-felt value, which is
     * most of the way across the table and passed every check correctly.
     */
    private static final float ON_THE_FELT = dev.gathering.core.ui.TableSurface.SPAN / 2f;

    @GameTest(template = "tables")
    public static void apointerReachesEverybodyElseAtTheTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());

        List<UUID> told = watch(pointing);
        TablePointing.handle(pointing, new TablePointPayload(origin, ON_THE_FELT, ON_THE_FELT, true));

        if (!told.contains(watching.getUUID())) {
            helper.fail("a seated player pointed and the person watching was not told: " + told);
            return;
        }
        if (told.contains(pointing.getUUID())) {
            helper.fail("a player was sent their own pointer, which nothing draws");
            return;
        }
        helper.succeed();
    }

    /**
     * The refusal that matters: a pointer from somebody not sitting there goes nowhere.
     * <p>Without the seat check in {@code TablePointing.handle} this test fails, which is the
     * only reason it is worth having. A client that could send one of these could reach into any
     * game in the world and move somebody's arm across it.
     */
    @GameTest(template = "tables")
    public static void apointerFromsomebodyNotSeatedIsDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        seat(level, origin, helper, 0);
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        stranger.setPos(origin.getCenter());

        List<UUID> told = watch(stranger);
        TablePointing.handle(stranger, new TablePointPayload(origin, ON_THE_FELT, ON_THE_FELT, true));

        if (!told.isEmpty()) {
            helper.fail("a player who is not seated at the table moved their arm over it: " + told);
            return;
        }
        helper.succeed();
    }

    /** And a point that is not on the felt at all, which is the other half of the same lie. */
    @GameTest(template = "tables")
    public static void apointOffTheFeltIsDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());

        List<UUID> told = watch(pointing);
        // Twice the width of the one table that is there, and then off the near edge.
        TablePointing.handle(pointing,
                new TablePointPayload(origin, dev.gathering.core.ui.TableSurface.SPAN * 2f,
                        ON_THE_FELT, true));
        TablePointing.handle(pointing, new TablePointPayload(origin, -5f, ON_THE_FELT, true));

        if (!told.isEmpty()) {
            helper.fail("a point off the edge of the table was passed on: " + told);
            return;
        }
        helper.succeed();
    }

    /**
     * Standing up puts the arm down, for everybody who was watching it.
     * <p>Through getting out of the chair rather than by calling the stop directly, because a seat
     * survives standing up and that is the whole trap: a player who stood and walked off went on
     * pointing at the felt from across the room, since everything asked whether they held a seat
     * and holding a seat is exactly what standing up does not change. The owner saw it in a
     * running game (2026-09-18). Whether the seat itself is kept is {@code AwayFromBoard}'s rule
     * and has its own tests; what is checked here is only that the arm comes down.
     */
    @GameTest(template = "tables")
    public static void standingUpStopsThePointer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());
        dev.gathering.block.ChairSeat chair = dev.gathering.block.ChairSeat.in(
                level, origin.above(), origin);
        level.addFreshEntity(chair);
        pointing.startRiding(chair, true);

        List<TablePointingPayload> said = java.util.Collections.synchronizedList(new ArrayList<>());
        UUID arm = pointing.getUUID();
        TablePointing.watchForTesting((to, payload) -> {
            if (arm.equals(payload.player())) {
                said.add(payload);
            }
        });
        TablePointing.handle(pointing, new TablePointPayload(origin, ON_THE_FELT, ON_THE_FELT, true));
        said.clear();
        // The real gesture: out of the chair.
        pointing.stopRiding();

        if (said.isEmpty()) {
            helper.fail("a player stood up and nobody was told, so the arm stays out over the felt");
            return;
        }
        if (said.stream().anyMatch(TablePointingPayload::pointing)) {
            helper.fail("stopping sent a pointer that is still pointing: " + said);
            return;
        }
        helper.succeed();
    }

    /**
     * A pointer naming a place nobody is near makes the server go nowhere.
     * <p>From the 2026-09-25 sweep. The seat was asked first, and asking means finding the
     * table, which on the server thread loads the chunk the payload names - and generates it if
     * nobody has ever been there. So every pointer at a new far-off position was one more chunk
     * made and saved, before any reach check or budget, as fast as a client could send them.
     * <p>A fixed position, so a run that fails this generates the one chunk and no more.
     */
    @GameTest(template = "tables")
    public static void apointerAtAFarPositionLoadsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        BlockPos far = new BlockPos(29_000_016, 64, 29_000_016);
        if (level.isLoaded(far)) {
            helper.fail("the fixture is wrong: the far-off chunk was loaded before anything was sent");
            return;
        }

        TablePointing.handle(pointing, new TablePointPayload(far, ON_THE_FELT, ON_THE_FELT, true));
        TablePointing.handle(pointing, new TablePointPayload(far, ON_THE_FELT, ON_THE_FELT, false));

        if (level.isLoaded(far)) {
            helper.fail("a pointer naming a position nobody is near loaded the chunk there");
            return;
        }
        helper.succeed();
    }

    /**
     * A stop from somebody who was never pointing goes to nobody.
     * <p>A stop needs no seat and spends no budget, so that standing up always puts the arm down -
     * and it went to everybody near whatever position it named. So a client could make the server
     * send a packet to every player in a crowded room for each one it sent, without ever pointing
     * at anything.
     */
    @GameTest(template = "tables")
    public static void astopFromSomebodyWhoWasNotPointingSaysNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        stranger.setPos(origin.getCenter());

        List<UUID> told = watch(stranger);
        for (int sent = 0; sent < 50; sent++) {
            TablePointing.handle(stranger, new TablePointPayload(origin, 0, 0, false));
        }

        if (!told.isEmpty()) {
            helper.fail(told.size() + " stop(s) passed on from a player who never pointed");
            return;
        }
        helper.succeed();
    }

    /** A stop still puts the arm down, once, for the table that saw it go up. */
    @GameTest(template = "tables")
    public static void astopPutsTheArmDownOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());
        List<TablePointingPayload> said = said(pointing, watching);

        TablePointing.handle(pointing, new TablePointPayload(origin, ON_THE_FELT, ON_THE_FELT, true));
        TablePointing.handle(pointing, new TablePointPayload(origin, 0, 0, false));
        TablePointing.handle(pointing, new TablePointPayload(origin, 0, 0, false));

        long stops = said.stream().filter(payload -> !payload.pointing()).count();
        if (stops != 1) {
            helper.fail("a player pointed and stopped, and the person watching was told " + stops
                    + " stop(s), not one");
            return;
        }
        helper.succeed();
    }

    /**
     * And it goes to the table the arm was over, whatever the stop names.
     * <p>A client whose last pointer was refused names that table in its stop, and a client that
     * lies names anywhere. Either way the people who saw the arm go up are the ones to tell.
     */
    @GameTest(template = "tables")
    public static void astopGoesToTheTableThatSawTheArm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());
        List<TablePointingPayload> said = said(pointing, watching);

        TablePointing.handle(pointing, new TablePointPayload(origin, ON_THE_FELT, ON_THE_FELT, true));
        TablePointing.handle(pointing, new TablePointPayload(origin.offset(5_000, 0, 5_000), 0, 0, false));

        if (said.stream().noneMatch(payload -> !payload.pointing() && payload.table().equals(origin))) {
            helper.fail("a stop naming somewhere else left the arm up over the table that saw it: " + said);
            return;
        }
        helper.succeed();
    }

    /** What this one player's arm was said to be doing, to this one watcher. */
    private static List<TablePointingPayload> said(ServerPlayer whose, ServerPlayer to) {
        List<TablePointingPayload> said = java.util.Collections.synchronizedList(new ArrayList<>());
        UUID arm = whose.getUUID();
        UUID watcher = to.getUUID();
        TablePointing.watchForTesting((sentTo, payload) -> {
            if (arm.equals(payload.player()) && watcher.equals(sentTo)) {
                said.add(payload);
            }
        });
        return said;
    }

    /**
     * Collects who this one player's pointers went to, one step before the wire.
     * <p>Filtered on whose arm it is, because the seam is one static field and the game tests in
     * a batch run alongside each other: an unfiltered collector picks up every other test's
     * pointers as well, which is how this test first failed while the code was right.
     * <p>Left in place rather than removed afterwards - a collector taken away mid-run is a
     * collector another test is still writing into.
     */
    private static List<UUID> watch(ServerPlayer whose) {
        List<UUID> told = java.util.Collections.synchronizedList(new ArrayList<>());
        UUID arm = whose.getUUID();
        TablePointing.watchForTesting((to, payload) -> {
            if (arm.equals(payload.player())) {
                told.add(to);
            }
        });
        return told;
    }

    /** One whole table, built out of its four parts. */
    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    private static ServerPlayer seat(ServerLevel level, BlockPos origin, GameTestHelper helper,
            int index) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(index);
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());
        return player;
    }
}
