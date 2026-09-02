package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.GatheringComponents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * All Fabric registration, in one class, mirroring the NeoForge bootstrap.
 * <p>The registered objects come from the same factories in {@code common}; only the
 * mechanism differs, which is exactly how large the platform-specific surface is meant to
 * be.
 */
final class GatheringRegistration {

    private GatheringRegistration() {
    }

    static void bootstrap() {
        // One list, walked, rather than three named registrations - see GatheringSounds. The
        // two loaders read the same list, which is what keeps them from ending up with
        // different sets of sounds under the same names.
        for (dev.gathering.registry.Registered<net.minecraft.sounds.SoundEvent> sound
                : dev.gathering.sound.GatheringSounds.all()) {
            String id = sound.entryName();
            sound.bindValue(Registry.register(BuiltInRegistries.SOUND_EVENT,
                    Gathering.id(id), dev.gathering.sound.GatheringSounds.create(id)));
        }

        // The block first: the table's item names its block when it is created.
        net.minecraft.world.level.block.Block table = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.TABLE_ID), GatheringContent.createTable());
        GatheringContent.TABLE.bindValue(table);

        GatheringContent.TABLE_ITEM.bindValue(Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.TABLE_ID),
                GatheringContent.createTableItem()));

        // The same table in other materials. Separate blocks that merge into one another's
        // clusters anyway, because every rule in the mod asks "is this a TableBlock".
        net.minecraft.world.level.block.Block cobblestoneTable = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.COBBLESTONE_TABLE_ID),
                GatheringContent.createCobblestoneTable());
        GatheringContent.COBBLESTONE_TABLE.bindValue(cobblestoneTable);
        GatheringContent.COBBLESTONE_TABLE_ITEM.bindValue(Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.COBBLESTONE_TABLE_ID),
                GatheringContent.createCobblestoneTableItem()));

        net.minecraft.world.level.block.Block blackstoneTable = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.BLACKSTONE_TABLE_ID),
                GatheringContent.createBlackstoneTable());
        GatheringContent.BLACKSTONE_TABLE.bindValue(blackstoneTable);
        GatheringContent.BLACKSTONE_TABLE_ITEM.bindValue(Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.BLACKSTONE_TABLE_ID),
                GatheringContent.createBlackstoneTableItem()));

        net.minecraft.world.level.block.Block cryingObsidianTable = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.CRYING_OBSIDIAN_TABLE_ID),
                GatheringContent.createCryingObsidianTable());
        GatheringContent.CRYING_OBSIDIAN_TABLE.bindValue(cryingObsidianTable);
        GatheringContent.CRYING_OBSIDIAN_TABLE_ITEM.bindValue(Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.CRYING_OBSIDIAN_TABLE_ID),
                GatheringContent.createCryingObsidianTableItem()));

        // Fabric's own builder, because BlockEntityType.Builder's supplier interface is
        // package-private in vanilla and only NeoForge access-transforms it.
        GatheringContent.TABLE_ENTITY.bindValue(Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Gathering.id(dev.gathering.block.TableBlockEntity.ID),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                        // Every table, or a stone one placed down has no block entity and
                        // therefore no session: vanilla's own valid-blocks check refuses
                        // the ones it was not told about.
                        .create(GatheringContent::createTableEntity, table,
                                cobblestoneTable, blackstoneTable, cryingObsidianTable)
                        .build()));

        Item card = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.CARD_ID), GatheringContent.createCard());
        Item deck = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.DECK_ID), GatheringContent.createDeck());

        Item pack = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.PACK_ID),
                GatheringContent.createPack());
        Item sealed = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.SEALED_ID),
                GatheringContent.createSealed());

        GatheringContent.CARD.bindValue(card);
        GatheringContent.DECK.bindValue(deck);
        GatheringContent.PACK.bindValue(pack);
        GatheringContent.SEALED.bindValue(sealed);

        GatheringComponents.CARD.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.CARD_ID),
                GatheringComponents.createCardType()));
        GatheringComponents.DECK.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.DECK_ID),
                GatheringComponents.createDeckType()));
        GatheringComponents.POOL.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.POOL_ID),
                GatheringComponents.createPoolType()));
        GatheringComponents.PACK.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.PACK_ID),
                GatheringComponents.createPackType()));
        GatheringComponents.SEALED.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.SEALED_ID),
                GatheringComponents.createSealedType()));
        GatheringComponents.STORY.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.STORY_ID),
                GatheringComponents.createStoryType()));

        net.minecraft.world.level.block.Block collection = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.COLLECTION_ID),
                GatheringContent.createCollection());
        GatheringContent.COLLECTION.bindValue(collection);
        GatheringContent.COLLECTION_ITEM.bindValue(Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.COLLECTION_ID),
                GatheringContent.createCollectionItem()));
        GatheringContent.COLLECTION_ENTITY.bindValue(Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Gathering.id(dev.gathering.block.CollectionBlockEntity.ID),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                        .create(GatheringContent::createCollectionEntity, collection)
                        .build()));

        // The counter, then somewhere to work at it, then the job. Fabric's helper registers
        // the point of interest and puts every one of the block's states into the map
        // villagers search, which is the part vanilla keeps to itself.
        net.minecraft.world.level.block.Block counter = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.SHOP_COUNTER_ID),
                GatheringContent.createShopCounter());
        GatheringContent.SHOP_COUNTER.bindValue(counter);
        Item counterItem = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.SHOP_COUNTER_ID),
                GatheringContent.createShopCounterItem());
        GatheringContent.SHOP_COUNTER_ITEM.bindValue(counterItem);
        dev.gathering.village.GatheringVillagers.COUNTER_POI.bindValue(
                net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper.register(
                        Gathering.id(GatheringContent.SHOP_COUNTER_ID), 1, 1, counter));
        dev.gathering.village.GatheringVillagers.SHOPKEEPER.bindValue(Registry.register(
                BuiltInRegistries.VILLAGER_PROFESSION,
                Gathering.id(dev.gathering.village.GatheringVillagers.SHOPKEEPER_ID),
                dev.gathering.village.GatheringVillagers.createShopkeeper()));
        for (int level = 1; level <= dev.gathering.core.sealed.ShopTier.LEVELS; level++) {
            List<net.minecraft.world.entity.npc.VillagerTrades.ItemListing> listings =
                    dev.gathering.village.ShopTrades.at(level);
            net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper
                    .registerVillagerOffers(
                            dev.gathering.village.GatheringVillagers.SHOPKEEPER.get(), level,
                            factories -> factories.addAll(listings));
        }

        dev.gathering.registry.GatheringLoot.SEALED_PRODUCT.bindValue(Registry.register(
                BuiltInRegistries.LOOT_POOL_ENTRY_TYPE,
                Gathering.id(dev.gathering.registry.GatheringLoot.SEALED_PRODUCT_ID),
                dev.gathering.registry.GatheringLoot.createSealedProductType()));

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Gathering.id("main"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup." + Gathering.MOD_ID + ".main"))
                        .icon(() -> new ItemStack(deck))
                        .displayItems((parameters, output) -> {
                            output.accept(new ItemStack(card));
                            output.accept(new ItemStack(deck));
                            output.accept(new ItemStack(pack));
                            output.accept(new ItemStack(sealed));
                            output.accept(new ItemStack(counterItem));
                            output.accept(new ItemStack(GatheringContent.TABLE_ITEM.get()));
                            output.accept(new ItemStack(
                                    GatheringContent.COBBLESTONE_TABLE_ITEM.get()));
                            output.accept(new ItemStack(
                                    GatheringContent.BLACKSTONE_TABLE_ITEM.get()));
                            output.accept(new ItemStack(
                                    GatheringContent.CRYING_OBSIDIAN_TABLE_ITEM.get()));
                            output.accept(
                                    new ItemStack(GatheringContent.COLLECTION_ITEM.get()));
                        })
                        .build());
    }
}
