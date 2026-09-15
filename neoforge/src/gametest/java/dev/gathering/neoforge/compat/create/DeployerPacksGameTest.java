package dev.gathering.neoforge.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gathering.Gathering;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Boosters opened on a Create Depot by an empty-handed Deployer. Registered only when Create is
 * installed - see PackGameTests. The Deployer's press is the one call it makes here; what is tested is
 * what that press does to the Depot.
 */
@PrefixGameTestTemplate(false)
public final class DeployerPacksGameTest {

    private DeployerPacksGameTest() {
    }

    /** A Deployer pressing on a booster on a Depot starts drawing it, once, however often it presses. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aPressOnABoosterStartsOneDraw(GameTestHelper helper) {
        BlockPos depot = depotWithABooster(helper);
        if (!DeployerPacks.tearOpen(helper.getLevel(), depot) || !DeployerPacks.isDrawingAt(helper.getLevel(), depot)) {
            helper.fail("a Deployer pressing on a booster on a Depot did not start drawing it");
            return;
        }
        if (!DeployerPacks.tearOpen(helper.getLevel(), depot)) {
            helper.fail("a second press while the booster is being drawn was not told a pack is already opening");
            return;
        }
        BlockPos bare = helper.absolutePos(new BlockPos(3, 2, 1));
        helper.getLevel().setBlock(bare, AllBlocks.DEPOT.getDefaultState(), 3);
        if (DeployerPacks.tearOpen(helper.getLevel(), bare)) {
            helper.fail("a press on an empty Depot claimed to open something");
            return;
        }
        helper.succeed();
    }

    /**
     * When the cards are ready, the booster becomes them on the Depot.
     * <p>Given the cards rather than waiting for them: drawing a real pack reaches Scryfall, which
     * a test run can be rate limited by, and a test that passes when the draw fails tests nothing.
     */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aBoosterOnADepotBecomesItsCards(GameTestHelper helper) {
        BlockPos depot = depotWithABooster(helper);
        DeployerPacks.swapForCards(helper.getLevel(), depot, BOOSTER, fifteenCards());
        DepotBehaviour behaviour = BlockEntityBehaviour.get(helper.getLevel(), depot, DepotBehaviour.TYPE);
        int cards = 0;
        int packs = 0;
        for (int slot = 0; slot < behaviour.itemHandler.getSlots(); slot++) {
            var stack = behaviour.itemHandler.getStackInSlot(slot);
            if (stack.getItem() instanceof CardItem) {
                cards += stack.getCount();
            }
            if (PackItem.packOf(stack).isPresent()) {
                packs += stack.getCount();
            }
        }
        for (ItemEntity dropped : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(depot).inflate(3))) {
            if (dropped.getItem().getItem() instanceof CardItem) {
                cards += dropped.getItem().getCount();
            }
        }
        if (packs != 0 || cards != 15) {
            helper.fail("a booster opened on a Depot left " + packs + " boosters and " + cards + " of its 15 cards");
            return;
        }
        helper.succeed();
    }

    /** A booster taken off the Depot before its cards are ready is not opened, and no cards appear for it. */
    @GameTest(templateNamespace = Gathering.MOD_ID, template = "empty")
    public static void aBoosterTakenAwayBeforeItsCardsAreReadyIsNotOpened(GameTestHelper helper) {
        BlockPos depot = depotWithABooster(helper);
        DepotBehaviour behaviour = BlockEntityBehaviour.get(helper.getLevel(), depot, DepotBehaviour.TYPE);
        behaviour.removeHeldItem();
        DeployerPacks.swapForCards(helper.getLevel(), depot, BOOSTER, fifteenCards());
        for (int slot = 0; slot < behaviour.itemHandler.getSlots(); slot++) {
            if (!behaviour.itemHandler.getStackInSlot(slot).isEmpty()) {
                helper.fail("cards appeared on the Depot for a booster that was taken away");
                return;
            }
        }
        if (!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(depot).inflate(3)).isEmpty()) {
            helper.fail("cards were dropped for a booster that was taken away");
            return;
        }
        helper.succeed();
    }

    private static final PackComponent BOOSTER = new PackComponent("m21", "draft");

    private static java.util.List<dev.gathering.core.card.CardIdentity> fifteenCards() {
        java.util.List<dev.gathering.core.card.CardIdentity> cards = new java.util.ArrayList<>();
        for (int index = 0; index < 15; index++) {
            cards.add(dev.gathering.core.card.CardIdentity.ofPrinting(new java.util.UUID(42L, index)));
        }
        return cards;
    }

    private static BlockPos depotWithABooster(GameTestHelper helper) {
        BlockPos depot = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(depot, AllBlocks.DEPOT.getDefaultState(), 3);
        DepotBehaviour behaviour = BlockEntityBehaviour.get(helper.getLevel(), depot, DepotBehaviour.TYPE);
        behaviour.setCenteredHeldItem(new TransportedItemStack(PackItem.of(BOOSTER)));
        return depot;
    }
}
