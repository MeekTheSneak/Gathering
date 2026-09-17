package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.sealed.MtgjsonProducts;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.core.sealed.SealedShelf;
import dev.gathering.item.GatheringContent;
import dev.gathering.village.GatheringVillagers;
import dev.gathering.village.ShopTrades;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Mana Coin: what a card shop takes, and where it comes from.
 * <p>The whole point of the coin is that it cannot be made, so there is nothing to test about
 * a recipe and everything to test about the two ends of it - a chest really produces one, and
 * the shopkeeper really asks for one. Both of those are wiring that fails silently: a coin that
 * never drops is a shop nobody can afford, and a price item that fell back to emeralds is the
 * farmable economy this replaced, running again with nothing to say so.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaCoinGameTest {

    private static final String COLLECTING_ON = "[modes]\ncollection_enabled = true\n";
    private static final String COLLECTING_OFF = "[modes]\ncollection_enabled = false\n";

    private static final ResourceLocation DUNGEON =
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon");

    /** The item itself, which everything else here depends on existing. */
    @GameTest(template = "empty")
    public static void theCoinIsARealItem(GameTestHelper helper) {
        if (!BuiltInRegistries.ITEM.containsKey(Gathering.id(GatheringContent.MANA_COIN_ID))) {
            helper.fail("gathering:mana_coin is not registered, so nothing can be priced in it");
            return;
        }
        ItemStack coins = new ItemStack(GatheringContent.MANA_COIN.get(), 36);
        if (coins.getCount() != 36) {
            helper.fail("A Mana Coin does not stack far enough to pay for a display box");
            return;
        }
        helper.succeed();
    }

    /**
     * Coins come out of an ordinary chest, and only with collecting on.
     * <p>Rolled through the real loot machinery rather than by asking the rule, because the
     * rule is pure and already checked in {@code :core}: what is checked here is that the
     * global loot modifier reaches the coin at all. It did not reach the archive pack on
     * Fabric for a release, which is exactly this shape of gap.
     * <p>Two hundred rolls at one chest in five is a coin somewhere in there unless the wiring
     * is broken; a run that finds none is not bad luck.
     */
    @GameTest(template = "empty")
    public static void anOrdinaryChestPaysCoins(GameTestHelper helper) {
        TestConfig.run(COLLECTING_ON, () -> {
            int found = coinsOutOf(helper, DUNGEON, 200);
            if (found == 0) {
                helper.fail("Two hundred dungeon chests held no Mana Coin, so nobody can ever "
                        + "afford anything at the shop");
                return;
            }
            helper.succeed();
        });
    }

    /** And a server that is not collecting has no currency, because it has no shop. */
    @GameTest(template = "empty")
    public static void collectingOffMeansNoCoins(GameTestHelper helper) {
        // Switched off here rather than assumed. These tests share one server with tests that
        // switch collecting on, so a test that reads a setting it did not set passes or fails
        // by the order the tests happened to run in.
        TestConfig.run(COLLECTING_OFF, () -> {
            if (coinsOutOf(helper, DUNGEON, 200) > 0) {
                helper.fail("A Mana Coin came out of a chest with collecting switched off");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The shop's own chest holds a few, which is a player's first coins.
     * <p>Walking into a card shop and finding the till empty is a dead end: the building is the
     * one place in the world that explains what the coin is for, so it is also the place that
     * hands over the first handful.
     */
    @GameTest(template = "empty")
    public static void theShopsChestHoldsAFew(GameTestHelper helper) {
        int fewest = Integer.MAX_VALUE;
        int most = 0;
        for (int roll = 0; roll < 32; roll++) {
            int coins = coinsOutOf(helper, Gathering.id("chests/card_shop"), 1);
            fewest = Math.min(fewest, coins);
            most = Math.max(most, coins);
        }
        if (fewest < 3 || most > 6) {
            helper.fail("A card shop's chest held between " + fewest + " and " + most
                    + " Mana Coins, which is not the few its loot table says");
            return;
        }
        helper.succeed();
    }

    /**
     * A shopkeeper asks for coins rather than emeralds.
     * <p>The price item is a config setting read through a registry lookup that falls back to
     * an emerald when the name does not resolve - which is the right thing to do for a server
     * that typed one wrong, and exactly wrong if the mod's own default ever stopped naming a
     * real item. Then every shop in the world would quietly go back to selling for emeralds,
     * and the only symptom would be an economy that feels too easy.
     */
    @GameTest(template = "empty")
    public static void theCounterAsksForCoins(GameTestHelper helper) {
        TestConfig.run(COLLECTING_ON, () -> {
            SealedProduct booster = new SealedProduct(
                    "pack-play", "Test play", "tst", "booster_pack", "play", 15,
                    new SealedProduct.Contents(
                            List.of(new SealedProduct.Booster("tst", "play")),
                            List.of(), List.of(), List.of(), List.of()));
            SealedShelf shelf = SealedShelf.of(
                    new MtgjsonProducts.Reading("tst", List.of(booster), List.of()), 1);
            Object was = dev.gathering.server.CardShop.stockForTesting(shelf);
            Villager villager = null;
            try {
                villager = EntityType.VILLAGER.create(helper.getLevel());
                if (villager == null) {
                    helper.fail("fixture: could not make a shopkeeper");
                    return;
                }
                villager.setPos(helper.absoluteVec(new BlockPos(1, 1, 1).getCenter()));
                villager.setVillagerData(new VillagerData(
                        VillagerType.PLAINS, GatheringVillagers.SHOPKEEPER.get(), 1));
                helper.getLevel().addFreshEntity(villager);

                MerchantOffer offer = ShopTrades.at(1, 0).get(0)
                        .getOffer(villager, helper.getLevel().getRandom());
                if (offer == null) {
                    helper.fail("fixture: a full shelf offered nothing at novice");
                    return;
                }
                ItemStack paid = offer.getCostA();
                if (!paid.is(GatheringContent.MANA_COIN.get())) {
                    helper.fail("A booster is sold for " + paid
                            + " rather than for Mana Coins, so the shop is farmable again");
                    return;
                }
                helper.succeed();
            } finally {
                if (villager != null) {
                    villager.discard();
                }
                dev.gathering.server.CardShop.restockForTesting(was);
            }
        });
    }

    /** How many Mana Coins this many rolls of one table produced, counted by stack size. */
    private static int coinsOutOf(GameTestHelper helper, ResourceLocation where, int rolls) {
        LootTable table = helper.getLevel().getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, where));
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);

        int coins = 0;
        for (int roll = 0; roll < rolls; roll++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(GatheringContent.MANA_COIN.get())) {
                    coins += stack.getCount();
                }
            }
        }
        return coins;
    }
}
