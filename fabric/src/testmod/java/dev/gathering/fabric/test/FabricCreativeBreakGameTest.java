package dev.gathering.fabric.test;

import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * A collection broken by its owner in creative comes up holding its cards, on this loader.
 * <p>A creative break never asks a block what it drops - the game removes it and stops - so a block
 * whose contents travel only in its drops lost them, whole collections at a click. What is checked here
 * is Fabric's own breaking path, which is vanilla's: NeoForge patches the same method, so the rule both
 * share is checked on each.
 */
public class FabricCreativeBreakGameTest implements FabricGameTest {

    private static final UUID FOREST = UUID.fromString("abababab-6666-4666-8666-666666666666");

    @GameTest(template = EMPTY_STRUCTURE)
    public void aCollectionBrokenInCreativeComesUpWithItsCards(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        BlockPos where = helper.absolutePos(at);
        helper.setBlock(at, GatheringContent.COLLECTION.get());
        CollectionBlockEntity collection = (CollectionBlockEntity) helper.getLevel().getBlockEntity(where);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.CREATIVE);
        collection.claimFor(owner.getUUID());
        collection.put(CardIdentity.ofPrinting(FOREST, false), 4);
        AABB around = new AABB(where).inflate(2.0);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);

        owner.gameMode.destroyBlock(where);

        if (helper.getLevel().getBlockState(where).is(GatheringContent.COLLECTION.get())) {
            helper.fail("the owner's creative break left the collection standing");
            return;
        }
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                item -> item.getItem().is(GatheringContent.COLLECTION_ITEM.get()));
        if (dropped.size() != 1 || dropped.get(0).getItem().get(DataComponents.BLOCK_ENTITY_DATA) == null) {
            helper.fail("a collection of four cards broken in creative came up as " + dropped.size()
                    + " collection item(s) holding them, not one");
            return;
        }
        dropped.forEach(ItemEntity::discard);
        helper.succeed();
    }
}
