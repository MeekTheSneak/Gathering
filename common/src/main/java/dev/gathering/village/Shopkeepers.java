package dev.gathering.village;

import dev.gathering.core.sealed.ShopCounter;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * Keeping every card shop the same shop.
 *
 * <p>Vanilla decides a villager's trades once, when they take the job, and never looks again.
 * That is right for a librarian and wrong here twice over: it would let somebody break the
 * counter and re-place it until the shelf offered what they wanted, and it would freeze one
 * shopkeeper on the stock of the afternoon they were hired while the shop down the road moved
 * on. So the offers are brought back in step whenever somebody walks up to one.
 *
 * <p>Bringing them back in step is not restocking. A product that is still on the counter keeps
 * the offer it had, with however many have been sold today still counted against it; only what
 * has actually left the shelf is replaced, and what replaces it is new stock and starts full.
 * Standing in front of a shopkeeper and closing the screen over and over gets you nothing.
 */
public final class Shopkeepers {

    /** How long a turnover is, in ticks, per hour of it. */
    private static final long TICKS_AN_HOUR = 20L * 60L * 60L;

    private Shopkeepers() {
    }

    /** Whether this villager is one of ours. */
    public static boolean isShopkeeper(Villager villager) {
        return villager != null && GatheringVillagers.SHOPKEEPER.isBound()
                && villager.getVillagerData().getProfession()
                        == GatheringVillagers.SHOPKEEPER.get();
    }

    /**
     * Which turnover the world is on.
     *
     * <p>Off the world's own clock rather than the wall's, so a server that was switched off
     * for a week comes back to the shelf it left rather than to a week of turnovers nobody
     * was there for. Every shop in the world reads the same number.
     */
    public static long rotation(Level level) {
        if (level == null) {
            return 0;
        }
        long hours = Math.max(1, ServerSettings.get().collecting().sealedRotationHours());
        return level.getGameTime() / (hours * TICKS_AN_HOUR);
    }

    /**
     * Brings one shopkeeper's counter up to date, before anybody looks at it.
     *
     * <p>Server thread only, and cheap: ten offers at most, compared by what they sell.
     */
    public static void refresh(Villager villager) {
        if (villager == null || villager.level().isClientSide() || !isShopkeeper(villager)) {
            return;
        }
        int level = villager.getVillagerData().getLevel();
        long rotation = rotation(villager.level());

        List<MerchantOffer> wanted = new ArrayList<>();
        for (int rung = 1; rung <= level; rung++) {
            for (VillagerTrades.ItemListing listing : ShopTrades.at(rung, rotation)) {
                MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
                if (offer != null) {
                    wanted.add(offer);
                }
            }
        }
        if (wanted.isEmpty()) {
            // The sets have not been read yet, or this server sells nothing. Leaving what is
            // there alone is the safe direction: a shopkeeper with yesterday's stock is better
            // than one with none.
            return;
        }

        MerchantOffers standing = villager.getOffers();
        List<MerchantOffer> kept = new ArrayList<>(standing);
        MerchantOffers brought = new MerchantOffers();
        for (MerchantOffer offer : wanted) {
            MerchantOffer already = matching(kept, offer.getResult());
            if (already != null) {
                kept.remove(already);
                brought.add(already);
            } else {
                brought.add(offer);
            }
        }
        standing.clear();
        standing.addAll(brought);
    }

    /** The standing offer selling this exact thing, or null. */
    private static MerchantOffer matching(List<MerchantOffer> standing, ItemStack result) {
        for (MerchantOffer offer : standing) {
            if (ItemStack.isSameItemSameComponents(offer.getResult(), result)) {
                return offer;
            }
        }
        return null;
    }
}
