package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.block.FurnitureDye;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
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
public final class FurnitureDyeGameTest {
    @GameTest(template = "empty")
    public static void dyeUsesRealBlockInteractionAndPreservesEntities(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (Block block : new Block[]{GatheringContent.SHOP_COUNTER.get(), GatheringContent.SCOREKEEPERS_DESK.get(), GatheringContent.COLLECTION.get()}) {
            helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
            var entity = helper.getLevel().getBlockEntity(pos);
            if (entity instanceof CollectionBlockEntity collection) collection.claimFor(player.getUUID());
            java.util.UUID event = java.util.UUID.randomUUID();
            if (entity instanceof ScorekeepersDeskBlockEntity desk) desk.runs(event);
            ItemStack dye = new ItemStack(Items.BLUE_DYE, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, dye);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            helper.getLevel().getBlockState(pos).useItemOn(dye, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            var painted = helper.getLevel().getBlockState(pos);
            helper.assertTrue(painted.getValue(FurnitureDye.FELT) == DyeColor.BLUE && dye.getCount() == 2, "dye must paint and consume once: " + block);
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) == entity, "dye replaced the block entity");
            if (entity instanceof ScorekeepersDeskBlockEntity desk) helper.assertTrue(desk.event().orElseThrow().equals(event), "dye lost tournament binding");
            painted.useItemOn(dye, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(dye.getCount() == 2, "same color consumed dye");
            var saved = net.minecraft.nbt.NbtUtils.writeBlockState(painted);
            var restored = net.minecraft.nbt.NbtUtils.readBlockState(helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
            helper.assertTrue(restored.equals(painted), "color did not survive block-state serialization");
            player.setGameMode(GameType.CREATIVE);
            ItemStack red = new ItemStack(Items.RED_DYE, 2);
            painted.useItemOn(red, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(red.getCount() == 2 && helper.getLevel().getBlockState(pos).getValue(FurnitureDye.FELT) == DyeColor.RED, "creative dye failed");
            player.setGameMode(GameType.SURVIVAL);
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectionVisitorCannotRecolorIt(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        ((CollectionBlockEntity) helper.getLevel().getBlockEntity(pos)).claimFor(java.util.UUID.randomUUID());
        ItemStack dye = new ItemStack(Items.BLUE_DYE, 3);
        var state = helper.getLevel().getBlockState(pos);
        state.useItemOn(dye, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(helper.getLevel().getBlockState(pos).equals(state) && dye.getCount() == 3, "visitor recolored a protected collection");
        helper.succeed();
    }
}
