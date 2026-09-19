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

    /** Standing up puts the arm down, for everybody who was watching it. */
    @GameTest(template = "tables")
    public static void standingUpStopsThePointer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer pointing = seat(level, origin, helper, 0);
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        watching.setPos(origin.getCenter());

        List<TablePointingPayload> said = java.util.Collections.synchronizedList(new ArrayList<>());
        UUID arm = pointing.getUUID();
        TablePointing.watchForTesting((to, payload) -> {
            if (arm.equals(payload.player())) {
                said.add(payload);
            }
        });
        TablePointing.handle(pointing, new TablePointPayload(origin, ON_THE_FELT, ON_THE_FELT, true));
        said.clear();
        TablePointing.stopped(pointing);

        if (said.isEmpty()) {
            helper.fail("a player stopped pointing and nobody was told, so the arm stays out");
            return;
        }
        if (said.stream().anyMatch(TablePointingPayload::pointing)) {
            helper.fail("stopping sent a pointer that is still pointing: " + said);
            return;
        }
        helper.succeed();
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
