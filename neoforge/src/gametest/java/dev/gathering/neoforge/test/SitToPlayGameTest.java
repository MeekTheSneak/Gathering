package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableBroadcast;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Playing takes sitting down.
 * <p>A seat outlives standing up, on purpose: that is what keeps it for somebody who has walked
 * off to a chest and for somebody who left the server mid-game. What it cost was a player who had
 * sat once being able to stand anywhere in reach of the table and play the whole game from there,
 * because every gesture asked only whether the seat was registered. The owner found it
 * (2026-09-18).
 * <p>So the two questions are now separate, and these hold them apart: the seat is what you keep,
 * and sitting is what lets you play.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SitToPlayGameTest {

    /** A player who holds a seat but is out of their chair is told to sit, and gets no board. */
    @GameTest(template = "tables")
    public static void astandingSeatHolderIsNotDealtIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(0);
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());
        if (TableSessions.start(level, origin, dev.gathering.core.match.MatchRules.single(
                dev.gathering.core.format.FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start at the table");
            return;
        }
        if (Chairs.isSittingAt(player, origin)) {
            helper.fail("the fixture put the player in a chair, so this checks nothing");
            return;
        }

        List<UUID> dealt = watch(player);
        click(helper, player, origin);

        if (dealt.contains(player.getUUID())) {
            helper.fail("a player standing away from their chair was dealt the board anyway");
            return;
        }
        // And the seat is still theirs: refusing to deal them in must not take it away.
        if (TableSeats.seatOf(level, origin, player.getUUID()).isEmpty()) {
            helper.fail("being told to sit back down cost the player their seat");
            return;
        }
        helper.succeed();
    }

    /** And the same player, once they are in the chair, is. */
    @GameTest(template = "tables")
    public static void asittingSeatHolderIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = table(helper);
        SeatAnchor anchor = TableClusters.at(level, origin).seats().get(0);
        BlockPos chair = TableClusters.seatPos(origin, anchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TableSeats.take(level, origin, anchor.cell(), anchor.side(), player.getUUID());
        // Put in the seat entity rather than sent through the chair block: what is being checked
        // here is the table's own question, and a fixture that also has to place a chair facing
        // the right way is a fixture that fails for reasons about chairs.
        dev.gathering.block.ChairSeat seat = dev.gathering.block.ChairSeat.in(level, chair, origin);
        level.addFreshEntity(seat);
        player.setPos(Vec3.atBottomCenterOf(chair));
        player.startRiding(seat, true);
        if (!Chairs.isSittingAt(player, origin)) {
            helper.fail("the fixture could not seat the player in a chair at the table");
            return;
        }
        if (TableSessions.start(level, origin, dev.gathering.core.match.MatchRules.single(
                dev.gathering.core.format.FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start at the table");
            return;
        }

        List<UUID> dealt = watch(player);
        click(helper, player, origin);

        if (!dealt.contains(player.getUUID())) {
            helper.fail("a player sitting in their chair was not dealt the board: " + dealt);
            return;
        }
        helper.succeed();
    }

    /**
     * Who has had a board built for them, which is what opening the table does.
     * <p>One step before the wire, because a stand-in player cannot take a payload. Filtered on
     * this test's own player, because the watchers are shared and the tests run alongside each
     * other.
     */
    private static List<UUID> watch(ServerPlayer whose) {
        List<UUID> dealt = java.util.Collections.synchronizedList(new ArrayList<>());
        UUID theirs = whose.getUUID();
        TableBroadcast.watchForTesting((player, view) -> {
            if (theirs.equals(player)) {
                dealt.add(player);
            }
        });
        return dealt;
    }

    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos origin) {
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin, false));
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
}
