package dev.gathering.village;

import dev.gathering.core.sealed.SealedShelf;
import dev.gathering.core.sealed.ShopCounter;
import dev.gathering.core.sealed.ShopPrice;
import dev.gathering.core.sealed.ShopTier;
import dev.gathering.server.CardShop;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * What a shopkeeper offers, at each level.
 *
 * <p>A vanilla trade list, built when a villager levels up rather than when the mod loads,
 * because what this server sells is not known until it has read what its sets were sold as. An
 * offer is asked for deep inside a villager's brain, so nothing here blocks or fetches: it
 * reads a shelf that was worked out at start and returns null if there is nothing at that
 * level yet, which vanilla skips over.
 *
 * <p>Every shopkeeper has the same shelf. Not a random two of what is at their level: the same
 * two, in every village, on every counter. A villager's trades are decided once, when they take
 * the job, so anything else would be a shelf you could break the counter and re-place until it
 * offered what you wanted - the librarian trick, aimed at boosters. Which two those are is
 * {@link ShopCounter}'s and is decided from the shelf, once.
 */
public final class ShopTrades {

    /** How many times one trade can be taken before restocking, per level. */
    private static final int[] USES = {12, 8, 5, 4, 3};

    /** What taking it once is worth to the shopkeeper, per level. */
    private static final int[] EXPERIENCE = {2, 5, 10, 15, 30};

    /** Vanilla's own demand multiplier. A thing everybody buys gets dearer, a little. */
    private static final float DEMAND = 0.05f;

    private ShopTrades() {
    }

    /** The listings for one level, as both loaders want them. */
    public static List<VillagerTrades.ItemListing> at(int level) {
        return at(level, -1);
    }

    /**
     * The listings for one level, at one turnover.
     *
     * <p>A turnover of -1 means "whatever the shelf is when you ask", which is what the
     * registered trade list wants: a villager taking the job reads the counter as it stands.
     * A number is used by {@link Shopkeepers} to bring an already-hired shopkeeper back in
     * step with everybody else.
     */
    public static List<VillagerTrades.ItemListing> at(int level, long rotation) {
        List<VillagerTrades.ItemListing> listings = new ArrayList<>(ShopCounter.PER_LEVEL);
        for (int choice = 0; choice < ShopCounter.PER_LEVEL; choice++) {
            listings.add(new Offer(level, choice, rotation));
        }
        return List.copyOf(listings);
    }

    /** Every level's listings, in order, for a loader that wants them all at once. */
    public static List<List<VillagerTrades.ItemListing>> all() {
        List<List<VillagerTrades.ItemListing>> levels = new ArrayList<>(ShopTier.LEVELS);
        for (int level = 1; level <= ShopTier.LEVELS; level++) {
            levels.add(at(level));
        }
        return List.copyOf(levels);
    }

    /** One thing on every shopkeeper's counter. */
    private record Offer(int level, int choice, long rotation)
            implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            long turnover = rotation >= 0
                    ? rotation
                    : Shopkeepers.rotation(trader == null ? null : trader.level());
            List<SealedShelf.Item> counter = CardShop.counterAt(level, turnover);
            if (choice >= counter.size()) {
                // Nothing at this level, or the sets have not been read yet. Vanilla skips a
                // null, so this is a villager with fewer trades rather than a broken one.
                return null;
            }
            SealedShelf.Item item = counter.get(choice);
            ItemStack result = CardShop.itemFor(item.product());
            if (result.isEmpty()) {
                return null;
            }

            var collecting = ServerSettings.get().collecting();
            int perBlock = collecting.sealedPriceBlockWorth();
            ShopPrice price = ShopPrice.of(item.price(), perBlock).orElse(null);
            if (price == null) {
                // Dearer than two stacks can carry. Not on this counter; a server that wants
                // to sell it can price a booster lower or its block higher.
                return null;
            }
            Item loose = itemNamed(collecting.sealedPriceItem(), Items.EMERALD);
            Item block = itemNamed(collecting.sealedPriceBlock(), Items.EMERALD_BLOCK);

            // The bigger pile first, so the trade reads as a price rather than as change.
            ItemCost first = price.blocks() > 0
                    ? new ItemCost(block, price.blocks())
                    : new ItemCost(loose, price.loose());
            Optional<ItemCost> second = price.blocks() > 0 && price.loose() > 0
                    ? Optional.of(new ItemCost(loose, price.loose()))
                    : Optional.empty();

            int tier = Math.clamp(level, 1, ShopTier.LEVELS) - 1;
            return new MerchantOffer(first, second, result,
                    USES[tier], EXPERIENCE[tier], DEMAND);
        }

    }

    /** The item a server named, or the one it meant if it named something that is not one. */
    private static Item itemNamed(String id, Item fallback) {
        ResourceLocation named = ResourceLocation.tryParse(id == null ? "" : id.trim());
        if (named == null) {
            return fallback;
        }
        Item found = BuiltInRegistries.ITEM.get(named);
        // A registry miss comes back as air rather than as nothing, which would be a shop
        // giving product away for free.
        return found == null || found == Items.AIR ? fallback : found;
    }
}
