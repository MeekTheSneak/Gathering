package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TrophyBlockEntity;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.TrophyComponent;
import dev.gathering.item.TrophyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A trophy standing on a shelf, and what it says while it stands there.
 * <p>The owner asked for the cup to be a block (2026-09-18) rather than an item in a chest, which
 * means the engraving now has to survive a journey it never used to make: off the stack, onto the
 * block, back onto the stack. Everything here is that round trip, because a trophy that comes back
 * up blank is a tournament nobody can prove happened.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrophyBlockGameTest {

    private static final TrophyComponent WON =
            new TrophyComponent("Friday Modern", "2026-09-18", "Bolt", 0x3F7FBF);

    /** Put down and broken again by a real pair of hands, still engraved and still the same color. */
    @GameTest(template = "tables")
    public static void aTrophyPutDownAndBrokenIsStillTheSameTrophy(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        ServerPlayer winner = helper.makeMockServerPlayerInLevel();
        place(helper, winner, at, TrophyItem.of(WON));

        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at))
                instanceof TrophyBlockEntity trophy)) {
            helper.fail("a trophy was put down and there is no trophy there");
            return;
        }
        if (!WON.equals(trophy.engraved())) {
            helper.fail("a trophy put down reads " + trophy.engraved() + " rather than " + WON);
            return;
        }

        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                trophy.getBlockState(), helper.getLevel(), helper.absolutePos(at), trophy, winner,
                ItemStack.EMPTY);
        if (drops.size() != 1 || !drops.get(0).is(GatheringContent.TROPHY.get())) {
            helper.fail("a broken trophy dropped " + drops);
            return;
        }
        TrophyComponent back = TrophyItem.trophyOf(drops.get(0)).orElse(null);
        if (!WON.equals(back)) {
            // The whole of the defect this guards: a cup of the right shape and nobody's name on it.
            helper.fail("a trophy picked back up reads " + back + " rather than " + WON);
            return;
        }
        helper.succeed();
    }

    /** And it is still there after the chunk has been written out and read back. */
    @GameTest(template = "tables")
    public static void theEngravingSurvivesASave(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        ServerPlayer winner = helper.makeMockServerPlayerInLevel();
        place(helper, winner, at, TrophyItem.of(WON));
        TrophyBlockEntity trophy = (TrophyBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(at));

        CompoundTag written = trophy.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.setBlock(new BlockPos(3, 2, 1),
                GatheringContent.TROPHY_BLOCK.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 1)))
                instanceof TrophyBlockEntity read)) {
            helper.fail("a trophy block was placed without its block entity");
            return;
        }
        read.loadWithComponents(written, helper.getLevel().registryAccess());
        if (!WON.equals(read.engraved())) {
            helper.fail("a trophy read back off the disk says " + read.engraved());
            return;
        }
        helper.succeed();
    }

    /**
     * A cup nobody won drops a cup nobody won.
     * <p>The other half of the round trip: the engraved path must not leak its fixture into a
     * trophy that never had one, which is how "every trophy is engraved with the last tournament"
     * would look.
     */
    @GameTest(template = "tables")
    public static void anUnengravedTrophyDropsAnUnengravedTrophy(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        place(helper, player, at, new ItemStack(GatheringContent.TROPHY.get()));
        TrophyBlockEntity trophy = (TrophyBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(at));
        if (trophy == null) {
            helper.fail("a plain trophy would not go down at all");
            return;
        }

        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                trophy.getBlockState(), helper.getLevel(), helper.absolutePos(at), trophy, player,
                ItemStack.EMPTY);
        if (drops.size() != 1 || !drops.get(0).is(GatheringContent.TROPHY.get())) {
            helper.fail("a broken plain trophy dropped " + drops);
            return;
        }
        if (TrophyItem.trophyOf(drops.get(0)).filter(TrophyComponent::isEngraved).isPresent()) {
            helper.fail("a trophy nobody won came up engraved: " + drops.get(0));
            return;
        }
        helper.succeed();
    }

    /** Placed by a real right-click, which is the only way a trophy reaches a shelf in play. */
    private static void place(GameTestHelper helper, ServerPlayer player, BlockPos at,
            ItemStack holding) {
        BlockPos under = at.below();
        helper.setBlock(under, net.minecraft.world.level.block.Blocks.STONE);
        BlockPos floor = helper.absolutePos(under);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, holding);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
    }
}
