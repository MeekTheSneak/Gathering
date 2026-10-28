package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.ChairSeat;
import dev.gathering.block.FurnitureDye;
import dev.gathering.block.TableSeats;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialChairGameTest {
    private static Block[] chairs() {
        return new Block[]{GatheringContent.COBBLESTONE_CHAIR.get(), GatheringContent.BLACKSTONE_CHAIR.get(), GatheringContent.CRYING_OBSIDIAN_CHAIR.get()};
    }

    @GameTest(template = "tables")
    public static void everyMaterialSeatsDyesAndReleasesTheTable(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos pos = table.offset(1, 0, -1);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for (Block block : chairs()) {
            helper.getLevel().setBlock(pos, block.defaultBlockState().setValue(ChairBlock.FACING, Direction.SOUTH), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(player.getVehicle() instanceof ChairSeat, "new chair did not seat the player: " + block);
            helper.assertTrue(TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isPresent(), "new chair did not claim table seat");
            var vehicle = player.getVehicle();
            ItemStack dye = new ItemStack(Items.CYAN_DYE, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, dye);
            player.gameMode.useItemOn(player, helper.getLevel(), dye, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(FurnitureDye.FELT) == DyeColor.CYAN && dye.getCount() == 1, "chair cushion did not dye");
            helper.assertTrue(player.getVehicle() == vehicle && TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isPresent(), "dyeing evicted its sitter or lost the claim");
            helper.getLevel().removeBlock(pos, false);
            helper.assertTrue(!player.isPassenger(), "removing the new chair left a rider");
            helper.assertTrue(TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isEmpty(), "removing the new chair left a claim");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyMaterialHasARecipeAndDropsItsOwnItem(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (Block block : chairs()) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(), "chair has no crafting recipe: " + id);
            helper.assertTrue(block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), "chair lacks pickaxe tag");
            var drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), "chair drops the wrong item: " + id);
        }
        helper.succeed();
    }
}
