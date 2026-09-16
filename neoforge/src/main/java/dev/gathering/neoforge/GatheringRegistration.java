package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.GatheringComponents;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All NeoForge registration, in one class.
 * <p>Concentrated deliberately: registration order across classes is classloading-dependent
 * and effectively invisible, so keeping it in one place makes the order explicit.
 */
final class GatheringRegistration {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Gathering.MOD_ID);

    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, Gathering.MOD_ID);

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Gathering.MOD_ID);

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, Gathering.MOD_ID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Gathering.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Gathering.MOD_ID);

    private static final DeferredRegister<net.minecraft.world.entity.ai.village.poi.PoiType> POI_TYPES =
            DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, Gathering.MOD_ID);

    private static final DeferredRegister<net.minecraft.world.entity.npc.VillagerProfession>
            PROFESSIONS = DeferredRegister.create(
                    BuiltInRegistries.VILLAGER_PROFESSION, Gathering.MOD_ID);

    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Gathering.MOD_ID);

    private static final DeferredRegister<net.minecraft.world.level.storage.loot.entries.LootPoolEntryType>
            LOOT_ENTRIES = DeferredRegister.create(
                    BuiltInRegistries.LOOT_POOL_ENTRY_TYPE, Gathering.MOD_ID);

    // The global loot modifier is what actually puts a pack in a chest on NeoForge; the
    // entry type beside it exists so a data pack can do the same by hand. See GatheringLoot.
    private static final DeferredRegister<com.mojang.serialization.MapCodec<
            ? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(
                    net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    Gathering.MOD_ID);

    private static final Supplier<Item> CARD =
            ITEMS.register(GatheringContent.CARD_ID, GatheringContent::createCard);
    private static final Supplier<Item> DECK =
            ITEMS.register(GatheringContent.DECK_ID, GatheringContent::createDeck);
    private static final Supplier<Item> PACK =
            ITEMS.register(GatheringContent.PACK_ID, GatheringContent::createPack);

    private static final Supplier<Item> SEALED =
            ITEMS.register(GatheringContent.SEALED_ID, GatheringContent::createSealed);

    private static final Supplier<Block> SHOP_COUNTER = BLOCKS.register(
            GatheringContent.SHOP_COUNTER_ID, GatheringContent::createShopCounter);
    private static final Supplier<Item> SHOP_COUNTER_ITEM = ITEMS.register(
            GatheringContent.SHOP_COUNTER_ID, GatheringContent::createShopCounterItem);

    // Registering the type is all it takes here: NeoForge's own registry callback puts every
    // one of its block states into the map villagers search, so nothing has to be told twice.
    private static final Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> COUNTER_POI =
            POI_TYPES.register(GatheringContent.SHOP_COUNTER_ID,
                    dev.gathering.village.GatheringVillagers::createCounterPoi);
    private static final Supplier<net.minecraft.world.entity.npc.VillagerProfession> SHOPKEEPER =
            PROFESSIONS.register(dev.gathering.village.GatheringVillagers.SHOPKEEPER_ID,
                    dev.gathering.village.GatheringVillagers::createShopkeeper);

    private static final Supplier<Block> COLLECTION =
            BLOCKS.register(GatheringContent.COLLECTION_ID, GatheringContent::createCollection);
    private static final Supplier<Item> COLLECTION_ITEM =
            ITEMS.register(GatheringContent.COLLECTION_ID, GatheringContent::createCollectionItem);
    private static final Supplier<BlockEntityType<dev.gathering.block.CollectionBlockEntity>>
            COLLECTION_ENTITY = BLOCK_ENTITIES.register(
                    dev.gathering.block.CollectionBlockEntity.ID, () -> BlockEntityType.Builder
                            .of(GatheringContent::createCollectionEntity,
                                    everyOne(GatheringContent.Woodwork.COLLECTION, COLLECTION))
                            .build(null));

    private static final Supplier<Block> CHAIR =
            BLOCKS.register(GatheringContent.CHAIR_ID, GatheringContent::createChair);
    private static final Supplier<Item> CHAIR_ITEM =
            ITEMS.register(GatheringContent.CHAIR_ID, GatheringContent::createChairItem);
    private static final Supplier<Block> COBBLESTONE_CHAIR = BLOCKS.register(GatheringContent.COBBLESTONE_CHAIR_ID,
            () -> GatheringContent.createStoneChair(net.minecraft.world.level.material.MapColor.STONE, 2.0f, net.minecraft.world.level.block.SoundType.STONE));
    private static final Supplier<Item> COBBLESTONE_CHAIR_ITEM = ITEMS.register(GatheringContent.COBBLESTONE_CHAIR_ID,
            () -> GatheringContent.createStoneChairItem(COBBLESTONE_CHAIR.get()));
    private static final Supplier<Block> BLACKSTONE_CHAIR = BLOCKS.register(GatheringContent.BLACKSTONE_CHAIR_ID,
            () -> GatheringContent.createStoneChair(net.minecraft.world.level.material.MapColor.COLOR_BLACK, 2.0f, net.minecraft.world.level.block.SoundType.STONE));
    private static final Supplier<Item> BLACKSTONE_CHAIR_ITEM = ITEMS.register(GatheringContent.BLACKSTONE_CHAIR_ID,
            () -> GatheringContent.createStoneChairItem(BLACKSTONE_CHAIR.get()));
    private static final Supplier<Block> CRYING_OBSIDIAN_CHAIR = BLOCKS.register(GatheringContent.CRYING_OBSIDIAN_CHAIR_ID,
            () -> GatheringContent.createStoneChair(net.minecraft.world.level.material.MapColor.COLOR_PURPLE, 5.0f, net.minecraft.world.level.block.SoundType.STONE));
    private static final Supplier<Item> CRYING_OBSIDIAN_CHAIR_ITEM = ITEMS.register(GatheringContent.CRYING_OBSIDIAN_CHAIR_ID,
            () -> GatheringContent.createStoneChairItem(CRYING_OBSIDIAN_CHAIR.get()));
    private static final Supplier<net.minecraft.world.entity.EntityType<dev.gathering.block.ChairSeat>> CHAIR_SEAT =
            ENTITY_TYPES.register(dev.gathering.block.ChairSeat.ID, GatheringContent::createChairSeat);

    private static final Supplier<Block> SCOREKEEPERS_DESK =
            BLOCKS.register(GatheringContent.SCOREKEEPERS_DESK_ID, GatheringContent::createScorekeepersDesk);
    private static final Supplier<Item> SCOREKEEPERS_DESK_ITEM =
            ITEMS.register(GatheringContent.SCOREKEEPERS_DESK_ID, GatheringContent::createScorekeepersDeskItem);
    private static final Supplier<BlockEntityType<dev.gathering.block.ScorekeepersDeskBlockEntity>>
            SCOREKEEPERS_DESK_ENTITY = BLOCK_ENTITIES.register(
                    dev.gathering.block.ScorekeepersDeskBlockEntity.ID, () -> BlockEntityType.Builder
                            .of(GatheringContent::createScorekeepersDeskEntity,
                                    everyOne(GatheringContent.Woodwork.SCOREKEEPERS_DESK, SCOREKEEPERS_DESK))
                            .build(null));

    private static final Supplier<Block> TABLE =
            BLOCKS.register(GatheringContent.TABLE_ID, GatheringContent::createTable);
    private static final Supplier<Item> TABLE_ITEM =
            ITEMS.register(GatheringContent.TABLE_ID, GatheringContent::createTableItem);

    // The same table in other materials. Registered beside the wooden one rather than
    // through it: they are separate blocks, they merge into one another's clusters anyway
    // because every rule in the mod asks "is this a TableBlock", and a build that wants a
    // stone table gets one without the wooden one having to know it exists.
    private static final Supplier<Block> COBBLESTONE_TABLE = BLOCKS.register(
            GatheringContent.COBBLESTONE_TABLE_ID, GatheringContent::createCobblestoneTable);
    private static final Supplier<Item> COBBLESTONE_TABLE_ITEM = ITEMS.register(
            GatheringContent.COBBLESTONE_TABLE_ID, GatheringContent::createCobblestoneTableItem);
    private static final Supplier<Block> BLACKSTONE_TABLE = BLOCKS.register(
            GatheringContent.BLACKSTONE_TABLE_ID, GatheringContent::createBlackstoneTable);
    private static final Supplier<Item> BLACKSTONE_TABLE_ITEM = ITEMS.register(
            GatheringContent.BLACKSTONE_TABLE_ID, GatheringContent::createBlackstoneTableItem);
    private static final Supplier<Block> CRYING_OBSIDIAN_TABLE = BLOCKS.register(
            GatheringContent.CRYING_OBSIDIAN_TABLE_ID,
            GatheringContent::createCryingObsidianTable);
    private static final Supplier<Item> CRYING_OBSIDIAN_TABLE_ITEM = ITEMS.register(
            GatheringContent.CRYING_OBSIDIAN_TABLE_ID,
            GatheringContent::createCryingObsidianTableItem);

    // BlockEntityType.Builder is only reachable here: its supplier interface is
    // package-private in vanilla and NeoForge access-transforms it. The null is the data
    // fixer type, which a mod has no business supplying.
    private static final Supplier<BlockEntityType<dev.gathering.block.TableBlockEntity>> TABLE_ENTITY =
            BLOCK_ENTITIES.register(dev.gathering.block.TableBlockEntity.ID, () ->
                    BlockEntityType.Builder
                            // Every table, or a stone one placed down has no block entity
                            // and therefore no session: "valid blocks" is vanilla's own
                            // check and it refuses the ones it was not told about.
                            .of(GatheringContent::createTableEntity, everyTable())
                            .build(null));

    private static final Supplier<DataComponentType<CardComponent>> CARD_COMPONENT =
            DATA_COMPONENTS.register(GatheringComponents.CARD_ID, GatheringComponents::createCardType);
    private static final Supplier<DataComponentType<DeckComponent>> DECK_COMPONENT =
            DATA_COMPONENTS.register(GatheringComponents.DECK_ID, GatheringComponents::createDeckType);
    private static final Supplier<DataComponentType<dev.gathering.item.DraftedPool>> POOL_COMPONENT =
            DATA_COMPONENTS.register(GatheringComponents.POOL_ID, GatheringComponents::createPoolType);
    private static final Supplier<DataComponentType<dev.gathering.item.PackComponent>> PACK_COMPONENT =
            DATA_COMPONENTS.register(GatheringComponents.PACK_ID, GatheringComponents::createPackType);
    private static final Supplier<DataComponentType<dev.gathering.item.SealedComponent>>
            SEALED_COMPONENT = DATA_COMPONENTS.register(
                    GatheringComponents.SEALED_ID, GatheringComponents::createSealedType);
    private static final Supplier<DataComponentType<dev.gathering.item.StoryComponent>>
            STORY_COMPONENT = DATA_COMPONENTS.register(
                    GatheringComponents.STORY_ID, GatheringComponents::createStoryType);
    private static final Supplier<DataComponentType<java.util.UUID>>
            DECK_HANDLE_COMPONENT = DATA_COMPONENTS.register(
                    GatheringComponents.DECK_HANDLE_ID,
                    GatheringComponents::createDeckHandleType);

    private static final Supplier<net.minecraft.world.level.storage.loot.entries.LootPoolEntryType>
            SEALED_PRODUCT_ENTRY = LOOT_ENTRIES.register(
                    dev.gathering.registry.GatheringLoot.SEALED_PRODUCT_ID,
                    dev.gathering.registry.GatheringLoot::createSealedProductType);

    private static final Supplier<com.mojang.serialization.MapCodec<
            ? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> SEALED_PRODUCT_MODIFIER =
            LOOT_MODIFIERS.register(
                    dev.gathering.registry.GatheringLoot.SEALED_PRODUCT_ID,
                    () -> dev.gathering.neoforge.loot.PackLootModifier.CODEC);

    private static final Supplier<CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Gathering.MOD_ID + ".main"))
            .icon(() -> new ItemStack(DECK.get()))
            .displayItems((parameters, output) -> {
                output.accept(new ItemStack(CARD.get()));
                output.accept(new ItemStack(DECK.get()));
                output.accept(new ItemStack(PACK.get()));
                output.accept(new ItemStack(SEALED.get()));
                output.accept(new ItemStack(SHOP_COUNTER_ITEM.get()));
                output.accept(new ItemStack(TABLE_ITEM.get()));
                output.accept(new ItemStack(COBBLESTONE_TABLE_ITEM.get()));
                output.accept(new ItemStack(BLACKSTONE_TABLE_ITEM.get()));
                output.accept(new ItemStack(CRYING_OBSIDIAN_TABLE_ITEM.get()));
                output.accept(new ItemStack(COLLECTION_ITEM.get()));
                output.accept(new ItemStack(SCOREKEEPERS_DESK_ITEM.get()));
                output.accept(new ItemStack(CHAIR_ITEM.get()));
                output.accept(new ItemStack(COBBLESTONE_CHAIR_ITEM.get()));
                output.accept(new ItemStack(BLACKSTONE_CHAIR_ITEM.get()));
                output.accept(new ItemStack(CRYING_OBSIDIAN_CHAIR_ITEM.get()));
                // And the same five things in the other woods, in the order the woods are listed.
                everyWoodenItem().forEach(item -> output.accept(new ItemStack(item)));
            })
            .build());

    /**
     * The same furniture in the other woods, registered from one list rather than fifty times over. Each one
     * is its own block and item, named for its wood, and everything that asks "is this a table" or "is this a
     * chair" answers the same for them - see GatheringContent.woodVariants.
     */
    private static final java.util.Map<String, Supplier<Block>> WOOD_BLOCKS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, Supplier<Item>> WOOD_ITEMS = new java.util.LinkedHashMap<>();

    static {
        for (GatheringContent.WoodVariant variant : GatheringContent.woodVariants()) {
            WOOD_BLOCKS.put(variant.id(), BLOCKS.register(variant.id(), variant::createBlock));
            WOOD_ITEMS.put(variant.id(), ITEMS.register(variant.id(), variant::createItem));
        }
    }

    /** The furniture in the other woods, as items, for the creative tab. */
    private static java.util.List<Item> everyWoodenItem() {
        return GatheringContent.woodVariants().stream()
                .map(variant -> WOOD_ITEMS.get(variant.id()).get()).toList();
    }

    /** Every table there is - four materials and the woods - for the table block entity's valid blocks. */
    private static Block[] everyTable() {
        java.util.List<Block> tables = new java.util.ArrayList<>(java.util.List.of(
                TABLE.get(), COBBLESTONE_TABLE.get(), BLACKSTONE_TABLE.get(), CRYING_OBSIDIAN_TABLE.get()));
        GatheringContent.woodVariants(GatheringContent.Woodwork.TABLE)
                .forEach(variant -> tables.add(WOOD_BLOCKS.get(variant.id()).get()));
        return tables.toArray(new Block[0]);
    }

    /** Every block of one kind, the plain one and the woods, for a block entity's valid blocks. */
    private static Block[] everyOne(GatheringContent.Woodwork kind, Supplier<Block> plain) {
        java.util.List<Block> blocks = new java.util.ArrayList<>();
        blocks.add(plain.get());
        GatheringContent.woodVariants(kind).forEach(variant -> blocks.add(WOOD_BLOCKS.get(variant.id()).get()));
        return blocks.toArray(new Block[0]);
    }

    private GatheringRegistration() {
    }

    static void bootstrap(IEventBus modBus) {
        // Blocks before items: the table's item names its block when it is created.
        BLOCKS.register(modBus);
        // After blocks: the counter's point of interest names every one of its block states.
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        SOUNDS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        LOOT_ENTRIES.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        CREATIVE_TABS.register(modBus);

        // One list, walked, rather than three named registrations - see GatheringSounds.
        for (dev.gathering.registry.Registered<net.minecraft.sounds.SoundEvent> sound
                : dev.gathering.sound.GatheringSounds.all()) {
            String id = sound.entryName();
            sound.bind(SOUNDS.register(id, () -> dev.gathering.sound.GatheringSounds.create(id)));
        }

        // Bound as suppliers, so this runs safely here in the constructor rather than
        // waiting on a lifecycle event: nothing is resolved until something asks.
        for (GatheringContent.WoodVariant variant : GatheringContent.woodVariants()) {
            variant.block().bind(WOOD_BLOCKS.get(variant.id()));
            variant.item().bind(WOOD_ITEMS.get(variant.id()));
        }
        GatheringContent.CARD.bind(CARD);
        GatheringContent.DECK.bind(DECK);
        GatheringContent.PACK.bind(PACK);
        GatheringContent.TABLE.bind(TABLE);
        GatheringContent.TABLE_ITEM.bind(TABLE_ITEM);
        GatheringContent.COBBLESTONE_TABLE.bind(COBBLESTONE_TABLE);
        GatheringContent.COBBLESTONE_TABLE_ITEM.bind(COBBLESTONE_TABLE_ITEM);
        GatheringContent.BLACKSTONE_TABLE.bind(BLACKSTONE_TABLE);
        GatheringContent.BLACKSTONE_TABLE_ITEM.bind(BLACKSTONE_TABLE_ITEM);
        GatheringContent.CRYING_OBSIDIAN_TABLE.bind(CRYING_OBSIDIAN_TABLE);
        GatheringContent.CRYING_OBSIDIAN_TABLE_ITEM.bind(CRYING_OBSIDIAN_TABLE_ITEM);
        GatheringContent.TABLE_ENTITY.bind(TABLE_ENTITY);
        GatheringComponents.CARD.bind(CARD_COMPONENT);
        GatheringComponents.DECK.bind(DECK_COMPONENT);
        GatheringComponents.POOL.bind(POOL_COMPONENT);
        GatheringComponents.PACK.bind(PACK_COMPONENT);
        GatheringComponents.SEALED.bind(SEALED_COMPONENT);
        GatheringComponents.STORY.bind(STORY_COMPONENT);
        GatheringComponents.DECK_HANDLE.bind(DECK_HANDLE_COMPONENT);
        GatheringContent.SEALED.bind(SEALED);
        GatheringContent.SHOP_COUNTER.bind(SHOP_COUNTER);
        GatheringContent.SHOP_COUNTER_ITEM.bind(SHOP_COUNTER_ITEM);
        dev.gathering.village.GatheringVillagers.COUNTER_POI.bind(COUNTER_POI);
        dev.gathering.village.GatheringVillagers.SHOPKEEPER.bind(SHOPKEEPER);
        dev.gathering.registry.GatheringLoot.SEALED_PRODUCT.bind(SEALED_PRODUCT_ENTRY);
        GatheringContent.COLLECTION.bind(COLLECTION);
        GatheringContent.COLLECTION_ITEM.bind(COLLECTION_ITEM);
        GatheringContent.COLLECTION_ENTITY.bind(COLLECTION_ENTITY);
        GatheringContent.CHAIR.bind(CHAIR);
        GatheringContent.CHAIR_ITEM.bind(CHAIR_ITEM);
        GatheringContent.COBBLESTONE_CHAIR.bind(COBBLESTONE_CHAIR);
        GatheringContent.COBBLESTONE_CHAIR_ITEM.bind(COBBLESTONE_CHAIR_ITEM);
        GatheringContent.BLACKSTONE_CHAIR.bind(BLACKSTONE_CHAIR);
        GatheringContent.BLACKSTONE_CHAIR_ITEM.bind(BLACKSTONE_CHAIR_ITEM);
        GatheringContent.CRYING_OBSIDIAN_CHAIR.bind(CRYING_OBSIDIAN_CHAIR);
        GatheringContent.CRYING_OBSIDIAN_CHAIR_ITEM.bind(CRYING_OBSIDIAN_CHAIR_ITEM);

        GatheringContent.CHAIR_SEAT.bind(CHAIR_SEAT);
        GatheringContent.SCOREKEEPERS_DESK.bind(SCOREKEEPERS_DESK);
        GatheringContent.SCOREKEEPERS_DESK_ITEM.bind(SCOREKEEPERS_DESK_ITEM);
        GatheringContent.SCOREKEEPERS_DESK_ENTITY.bind(SCOREKEEPERS_DESK_ENTITY);
    }

    static Supplier<CreativeModeTab> creativeTab() {
        return TAB;
    }
}
