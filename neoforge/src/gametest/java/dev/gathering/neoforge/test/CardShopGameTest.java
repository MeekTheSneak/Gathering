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
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The card shop, in a running server.
 * <p>What is worth checking here rather than on paper is the wiring. The rules about what is
 * on a shelf and what it costs are pure and are checked in {@code :core}; what cannot be
 * checked there is whether the counter is really somewhere a villager can work, whether the
 * profession registered at all, and whether a box somebody bought on another server does
 * anything worse than nothing.
 * <p>Nothing here trades. A shelf needs the published data for a set, which needs a network,
 * and a game test that reached one would be a game test that fails when somebody else's host
 * is down.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardShopGameTest {

    /**
     * A counter a villager cannot see is a shop that never opens.
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
     * Somebody walking up to a shopkeeper is what stocks the shelf.
     * <p>Which sets are behind a counter depends on the turnover the world is on, and there
     * is no world to ask when a server is still starting - so nothing is read at start and
     * the first shopkeeper anybody looks at does the asking. If that link were ever broken
     * the shop would simply be empty forever, with nothing in any log to say why.
     */
    @GameTest(template = "empty")
    public static void lookingAtAShopkeeperStocksTheShelf(GameTestHelper helper) {
        TestConfig.run("[modes]\ncollection_enabled = true\n", () -> {
            dev.gathering.server.CardShop.clear();
            if (dev.gathering.server.CardShop.stockedFor() != -1) {
                helper.fail("A cleared shop still says which turnover it is stocked for");
                return;
            }
            Villager villager = EntityType.VILLAGER.create(helper.getLevel());
            if (villager == null) {
                helper.fail("Could not make a villager to stand behind the counter");
                return;
            }
            villager.setPos(helper.absoluteVec(new BlockPos(1, 1, 1).getCenter()));
            villager.setVillagerData(new VillagerData(
                    VillagerType.PLAINS, GatheringVillagers.SHOPKEEPER.get(), 3));
            helper.getLevel().addFreshEntity(villager);

            Shopkeepers.refresh(villager);
            // Either the reading is under way or it has already finished. What must not have
            // happened is nothing at all.
            boolean asked = dev.gathering.server.CardShop.isRestocking()
                    || dev.gathering.server.CardShop.stockedFor() != -1;
            villager.discard();
            if (!asked) {
                helper.fail("Looking at a shopkeeper did not start the shelf being read, so "
                        + "this server's shop would stay empty forever");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Walking up to a shopkeeper twice changes nothing.
     * <p>Their counter is brought back in step every time somebody looks at it, which is what
     * keeps every card shop the same shop. The thing that must never follow is a restock: if
     * looking again reset what had been sold, standing in front of one and closing the screen
     * over and over would be an infinite supply of boosters.
     * <p>True whether or not this server has anything to stock, which is why it is the thing
     * checked rather than a count.
     */
    @GameTest(template = "empty")
    public static void lookingTwiceChangesNothing(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) {
            helper.fail("Could not make a villager to stand behind the counter");
            return;
        }
        villager.setPos(helper.absoluteVec(new BlockPos(1, 1, 1).getCenter()));
        villager.setVillagerData(new VillagerData(
                VillagerType.PLAINS, GatheringVillagers.SHOPKEEPER.get(), 3));
        helper.getLevel().addFreshEntity(villager);

        // Tried again on a later tick rather than failed on the first: the shelf is read on a
        // worker and other tests change what this server stocks, so a new shelf can land between
        // the two looks, and the count follows it. A look that really restocks fails on every
        // tick it is tried, and the test times out on that message.
        helper.succeedWhen(() -> {
            Shopkeepers.refresh(villager);
            List<ItemStack> first = new java.util.ArrayList<>();
            List<Integer> sold = new java.util.ArrayList<>();
            villager.getOffers().forEach(offer -> {
                first.add(offer.getResult());
                sold.add(offer.getUses());
            });

            Shopkeepers.refresh(villager);
            if (villager.getOffers().size() != first.size()) {
                helper.fail("Looking at a shopkeeper twice changed how much they sell");
            }
            for (int slot = 0; slot < first.size(); slot++) {
                var again = villager.getOffers().get(slot);
                if (!ItemStack.isSameItemSameComponents(again.getResult(), first.get(slot))
                        || again.getUses() != sold.get(slot)) {
                    helper.fail("Looking at a shopkeeper twice restocked them");
                }
            }
            villager.discard();
        });
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

    /**
     * A shopkeeper somebody is already trading with does not change stock under them.
     * <p>The refresh runs as a player right-clicks, before the game decides the villager is busy -
     * so a second player walking up at a turnover moved the first player's offers under their open
     * screen, and the slot they pressed paid for whatever had moved into it.
     */
    @GameTest(template = "empty")
    public static void aShopkeeperMidTradeKeepsTheirStock(GameTestHelper helper) {
        dev.gathering.core.sealed.SealedProduct booster = new dev.gathering.core.sealed.SealedProduct(
                "pack-play", "Test play", "tst", "booster_pack", "play", 15,
                new dev.gathering.core.sealed.SealedProduct.Contents(
                        List.of(new dev.gathering.core.sealed.SealedProduct.Booster("tst", "play")),
                        List.of(), List.of(), List.of(), List.of()));
        dev.gathering.core.sealed.SealedShelf shelf = dev.gathering.core.sealed.SealedShelf.of(
                new dev.gathering.core.sealed.MtgjsonProducts.Reading("tst", List.of(booster), List.of()), 2);
        Object was = dev.gathering.server.CardShop.stockForTesting(shelf);
        try {
            Villager free = shopkeeper(helper, 1);
            Villager busy = shopkeeper(helper, 3);
            if (free == null || busy == null) {
                helper.fail("fixture: could not make the shopkeepers");
                return;
            }
            Shopkeepers.refresh(free);
            if (free.getOffers().isEmpty()) {
                helper.fail("fixture: a shopkeeper nobody is trading with stocked nothing from a full shelf");
                return;
            }
            busy.setTradingPlayer(helper.makeMockServerPlayerInLevel());
            Shopkeepers.refresh(busy);
            if (!busy.getOffers().isEmpty()) {
                helper.fail("a shopkeeper's offers changed while somebody was trading with them");
                return;
            }
            helper.succeed();
        } finally {
            dev.gathering.server.CardShop.restockForTesting(was);
        }
    }

    private static Villager shopkeeper(GameTestHelper helper, int x) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) {
            return null;
        }
        villager.setPos(helper.absoluteVec(new BlockPos(x, 1, 1).getCenter()));
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, GatheringVillagers.SHOPKEEPER.get(), 1));
        villager.getOffers().clear();
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }
}
