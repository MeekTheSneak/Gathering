package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.SealedComponent;
import dev.gathering.item.SealedItem;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.village.GatheringVillagers;
import dev.gathering.village.ShopTrades;
import dev.gathering.village.Shopkeepers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The card shop, in a running server.
 *
 * <p>What is worth checking here rather than on paper is the wiring. The rules about what is
 * on a shelf and what it costs are pure and are checked in {@code :core}; what cannot be
 * checked there is whether the counter is really somewhere a villager can work, whether the
 * profession registered at all, and whether a box somebody bought on another server does
 * anything worse than nothing.
 *
 * <p>Nothing here trades. A shelf needs the published data for a set, which needs a network,
 * and a game test that reached one would be a game test that fails when somebody else's host
 * is down.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardShopGameTest {

    /**
     * A counter a villager cannot see is a shop that never opens.
     *
     * <p>Registering the point of interest is not enough on its own: every one of the block's
     * states has to be in the map villagers search, and the two loaders do that in different
     * ways. A rotation nobody registered would be a counter that works three ways out of four.
     */
    @GameTest(template = "empty")
    public static void everyWayUpTheCounterIsAJobSite(GameTestHelper helper) {
        for (BlockState state
                : GatheringContent.SHOP_COUNTER.get().getStateDefinition().getPossibleStates()) {
            if (PoiTypes.forState(state).isEmpty()) {
                helper.fail("A shop counter facing " + state + " is not somewhere to work");
            }
        }
        helper.succeed();
    }

    /** And the job itself, which is the other half of the same wiring. */
    @GameTest(template = "empty")
    public static void theShopkeeperIsARealProfession(GameTestHelper helper) {
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(
                Gathering.id(GatheringVillagers.SHOPKEEPER_ID))) {
            helper.fail("Nobody can work in a card shop: the profession is not registered");
        }
        helper.succeed();
    }

    /**
     * Bringing a counter back in step must not empty it.
     *
     * <p>A server that has not read its sets yet has nothing to put on a counter, and the one
     * thing that must not happen then is a shopkeeper losing the trades they already had.
     */
    @GameTest(template = "empty")
    public static void nothingToStockLeavesTheCounterAlone(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) {
            helper.fail("Could not make a villager to stand behind the counter");
            return;
        }
        villager.setPos(helper.absoluteVec(new BlockPos(1, 1, 1).getCenter()));
        villager.setVillagerData(new VillagerData(
                VillagerType.PLAINS, GatheringVillagers.SHOPKEEPER.get(), 3));
        helper.getLevel().addFreshEntity(villager);

        int before = villager.getOffers().size();
        Shopkeepers.refresh(villager);
        if (villager.getOffers().size() != before) {
            helper.fail("A shopkeeper with nothing to stock lost what they had");
        }
        villager.discard();
        helper.succeed();
    }

    /** Every level offers as many things as a villager can be given, and no more. */
    @GameTest(template = "empty")
    public static void everyLevelOffersWhatAVillagerCanHold(GameTestHelper helper) {
        for (int level = 1; level <= dev.gathering.core.sealed.ShopTier.LEVELS; level++) {
            if (ShopTrades.at(level).size()
                    != dev.gathering.core.sealed.ShopCounter.PER_LEVEL) {
                helper.fail("Level " + level + " does not offer what a villager can hold");
            }
        }
        helper.succeed();
    }

    /**
     * A box this server has never heard of does nothing, and is not eaten.
     *
     * <p>Bought on a server pointed at one set and brought to a server pointed at another,
     * or written by hand by an operator. Neither is the player's fault and neither should
     * cost them the box.
     */
    @GameTest(template = "empty")
    public static void anUnknownBoxIsNotDestroyed(GameTestHelper helper) {
        ItemStack box = SealedItem.of(new SealedComponent("tst", "nobody", "A box of nothing"));
        if (box.get(GatheringComponents.SEALED.get()) == null) {
            helper.fail("A sealed box does not carry what it is");
        }
        if (dev.gathering.server.CardShop.openingOf(
                box.get(GatheringComponents.SEALED.get())).isEmpty()) {
            helper.succeed();
            return;
        }
        helper.fail("A box this server cannot look up opened into something");
    }
}
