package dev.gathering.village;

import com.google.common.collect.ImmutableSet;
import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.Registered;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * The card shop's keeper, as a village job.
 * <p>A profession rather than a block with a screen in it. Everything a player already knows
 * about villagers is true here: the counter is a workstation an unemployed villager takes, the
 * trades are vanilla trades in the vanilla screen, restocking works the way restocking works,
 * and trading with them levels them up. Nothing about buying a booster needs explaining to
 * somebody who has bought a book from a librarian.
 * <p>What that buys beyond familiarity is the shape of the shelf. A novice has packs; the boxes
 * and the Commander decks are further up; a case is something you get from a shopkeeper you
 * have been trading with for a while. So "anything bigger than a booster is bought rather than
 * found" comes with its own progression instead of a price wall.
 */
public final class GatheringVillagers {

    public static final String SHOPKEEPER_ID = "shopkeeper";

    /** Named here so the profession can ask for it before either exists. */
    public static final ResourceKey<PoiType> COUNTER_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    Gathering.MOD_ID, GatheringContent.SHOP_COUNTER_ID));

    public static final Registered<PoiType> COUNTER_POI =
            new Registered<>(GatheringContent.SHOP_COUNTER_ID);
    public static final Registered<VillagerProfession> SHOPKEEPER =
            new Registered<>(SHOPKEEPER_ID);

    private GatheringVillagers() {
    }

    /**
     * The counter, as somewhere a villager can work.
     * <p>One ticket, so one villager per counter: two shopkeepers sharing a till is not a shop
     * anybody would recognize. Searched from a block away, like every other workstation.
     */
    public static PoiType createCounterPoi() {
        return new PoiType(
                ImmutableSet.copyOf(
                        GatheringContent.SHOP_COUNTER.get().getStateDefinition().getPossibleStates()),
                1, 1);
    }

    /**
     * The job itself.
     * <p>Held and acquirable are the same predicate, which is what every vanilla profession
     * does: the counter you can take is the counter you keep.
     */
    public static VillagerProfession createShopkeeper() {
        return new VillagerProfession(
                SHOPKEEPER_ID,
                holder -> holder.is(COUNTER_KEY),
                holder -> holder.is(COUNTER_KEY),
                ImmutableSet.of(),
                ImmutableSet.of(),
                // Paper, near enough. There is no card-shuffling sound in vanilla and a
                // librarian's is the one that already means somebody handling stock.
                SoundEvents.VILLAGER_WORK_LIBRARIAN);
    }
}
