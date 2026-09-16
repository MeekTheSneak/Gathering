package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.CollectionBlock;
import dev.gathering.block.ScorekeepersDeskBlock;
import dev.gathering.block.ShopCounterBlock;
import dev.gathering.block.TableBlock;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The same five wooden things in every wood: registered, the kind of block they say they are, and each one
 * placed for real with the block entity the plain one has.
 * <p>A table in spruce that is not a TableBlock, or a collection in cherry whose block entity type refuses it,
 * would be a block that looks right and does nothing - and fifty of them are not something anybody checks by
 * hand.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoodVariantsGameTest {

    private WoodVariantsGameTest() {
    }

    /** Every variant is a real block with a real item, of the kind it is named for. */
    @GameTest(template = "empty")
    public static void everyWoodIsRegistered(GameTestHelper helper) {
        for (GatheringContent.WoodVariant variant : GatheringContent.woodVariants()) {
            Block block = variant.block().get();
            if (!BuiltInRegistries.BLOCK.containsKey(Gathering.id(variant.id()))) {
                helper.fail("no block registered as " + variant.id());
                return;
            }
            if (!(BuiltInRegistries.ITEM.get(Gathering.id(variant.id())) instanceof BlockItem item)
                    || item.getBlock() != block) {
                helper.fail("the item " + variant.id() + " does not place the block of the same name");
                return;
            }
            boolean right = switch (variant.kind()) {
                case TABLE -> block instanceof TableBlock;
                case CHAIR -> block instanceof ChairBlock;
                case SHOP_COUNTER -> block instanceof ShopCounterBlock;
                case COLLECTION -> block instanceof CollectionBlock;
                case SCOREKEEPERS_DESK -> block instanceof ScorekeepersDeskBlock;
            };
            if (!right) {
                helper.fail(variant.id() + " is a " + block.getClass().getSimpleName()
                        + ", not the " + variant.kind() + " it is named for");
                return;
            }
        }
        if (GatheringContent.woodVariants().size() != 50) {
            helper.fail("five wooden things in ten other woods is fifty blocks, not "
                    + GatheringContent.woodVariants().size());
            return;
        }
        helper.succeed();
    }

    /**
     * Each one placed in the world keeps the block entity its plain twin has: a table in every wood can hold a
     * game, a collection in every wood can hold cards, and a desk in every wood can run a tournament. Vanilla
     * refuses a block entity for a block its type was not told about, quietly.
     */
    @GameTest(template = "tables")
    public static void everyWoodPlacesAndKeepsItsBlockEntity(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        for (GatheringContent.WoodVariant variant : GatheringContent.woodVariants()) {
            BlockState state = variant.block().get().defaultBlockState();
            helper.setBlock(at, state);
            boolean wanted = switch (variant.kind()) {
                case TABLE, COLLECTION, SCOREKEEPERS_DESK -> true;
                case CHAIR, SHOP_COUNTER -> false;
            };
            boolean found = helper.getLevel().getBlockEntity(helper.absolutePos(at)) != null;
            if (found != wanted) {
                helper.fail(variant.id() + (wanted ? " has no block entity" : " grew a block entity"));
                return;
            }
            helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
        }
        helper.succeed();
    }
}
