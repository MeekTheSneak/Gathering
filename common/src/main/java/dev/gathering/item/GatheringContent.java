package dev.gathering.item;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableBlockItem;
import dev.gathering.registry.Registered;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Everything the mod registers into vanilla registries, named once and bound by whichever
 * loader is running.
 * <p>The factories live here rather than in the loader modules so both loaders register
 * identical objects; only the registration mechanism differs between them, which is exactly
 * the size the platform-specific surface is supposed to be.
 */
public final class GatheringContent {

    public static final String CARD_ID = "card";
    public static final String DECK_ID = "deck";
    public static final String PACK_ID = "pack";
    public static final String TABLE_ID = "table";

    /**
     * The tables that are not wooden.
     * <p>A cosmetic family with identical function, which the brief has always asked for: a
     * table is a table whatever it is made of, they cluster into one another regardless of
     * skin, and nothing in the game asks which one it is sitting at. Only the look changes -
     * and the look changes properly, in the shape as well as the material, because a stone
     * table that is the wooden one with a different texture is a recolor rather than a table
     * somebody would choose for a build.
     */
    public static final String COBBLESTONE_TABLE_ID = "cobblestone_table";
    public static final String BLACKSTONE_TABLE_ID = "blackstone_table";
    public static final String CRYING_OBSIDIAN_TABLE_ID = "crying_obsidian_table";
    public static final String COLLECTION_ID = "collection";
    public static final String SEALED_ID = "sealed";
    public static final String SHOP_COUNTER_ID = "shop_counter";

    public static final Registered<Item> CARD = new Registered<>(CARD_ID);
    public static final Registered<Item> DECK = new Registered<>(DECK_ID);
    public static final Registered<Item> PACK = new Registered<>(PACK_ID);
    public static final Registered<Block> TABLE = new Registered<>(TABLE_ID);
    public static final Registered<Item> TABLE_ITEM = new Registered<>(TABLE_ID);
    public static final Registered<Block> COBBLESTONE_TABLE = new Registered<>(COBBLESTONE_TABLE_ID);
    public static final Registered<Item> COBBLESTONE_TABLE_ITEM = new Registered<>(COBBLESTONE_TABLE_ID);
    public static final Registered<Block> BLACKSTONE_TABLE = new Registered<>(BLACKSTONE_TABLE_ID);
    public static final Registered<Item> BLACKSTONE_TABLE_ITEM = new Registered<>(BLACKSTONE_TABLE_ID);
    public static final Registered<Block> CRYING_OBSIDIAN_TABLE =
            new Registered<>(CRYING_OBSIDIAN_TABLE_ID);
    public static final Registered<Item> CRYING_OBSIDIAN_TABLE_ITEM =
            new Registered<>(CRYING_OBSIDIAN_TABLE_ID);

    /** Every table, in the order they are offered. What one hand needs, the others need. */
    public static java.util.List<Registered<Block>> tables() {
        return java.util.List.of(TABLE, COBBLESTONE_TABLE, BLACKSTONE_TABLE, CRYING_OBSIDIAN_TABLE);
    }

    /** Every table's item, in the same order. */
    public static java.util.List<Registered<Item>> tableItems() {
        return java.util.List.of(
                TABLE_ITEM, COBBLESTONE_TABLE_ITEM, BLACKSTONE_TABLE_ITEM,
                CRYING_OBSIDIAN_TABLE_ITEM);
    }
    public static final Registered<BlockEntityType<TableBlockEntity>> TABLE_ENTITY =
            new Registered<>(TableBlockEntity.ID);
    public static final Registered<Item> SEALED = new Registered<>(SEALED_ID);
    public static final Registered<Block> SHOP_COUNTER = new Registered<>(SHOP_COUNTER_ID);
    public static final Registered<Item> SHOP_COUNTER_ITEM = new Registered<>(SHOP_COUNTER_ID);
    public static final Registered<Block> COLLECTION = new Registered<>(COLLECTION_ID);
    public static final Registered<Item> COLLECTION_ITEM = new Registered<>(COLLECTION_ID);
    public static final Registered<BlockEntityType<dev.gathering.block.CollectionBlockEntity>>
            COLLECTION_ENTITY = new Registered<>(
                    dev.gathering.block.CollectionBlockEntity.ID);

    private GatheringContent() {
    }

    /** Cards never stack: two cards are two objects, even when they are the same printing. */
    public static Item.Properties cardProperties() {
        return new Item.Properties().stacksTo(1);
    }

    public static Item createCard() {
        return new CardItem(cardProperties());
    }

    public static Item createDeck() {
        return new DeckItem(new Item.Properties().stacksTo(1));
    }

    /**
     * Packs stack, unlike cards and decks.
     * <p>Two sealed boosters of the same product are interchangeable in a way two cards never
     * are - nobody has looked inside either - so a box of thirty is one slot rather than
     * thirty, which is the difference between a display box being a thing you can carry and a
     * thing you cannot.
     */
    public static Item createPack() {
        return new PackItem(new Item.Properties().stacksTo(16));
    }

    /**
     * Boxes stack the way packs do, and for the same reason.
     * <p>Not as far, though. A box is a thing you buy one or two of and open; a chest with
     * sixteen cases in it is not a collection, it is a warehouse.
     */
    public static Item createSealed() {
        return new SealedItem(new Item.Properties().stacksTo(8));
    }

    /**
     * The counter a shopkeeper works behind.
     * <p>An ordinary workstation: put one down in a village and an unemployed villager takes
     * the job, exactly the way a lectern or a grindstone works. Nothing about it needs
     * explaining to somebody who has played Minecraft.
     */
    public static Block createShopCounter() {
        return new dev.gathering.block.ShopCounterBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .sound(SoundType.WOOD));
    }

    public static Item createShopCounterItem() {
        return new net.minecraft.world.item.BlockItem(
                SHOP_COUNTER.get(), new Item.Properties());
    }

    public static Block createTable() {
        return new TableBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                // The model is a table rather than a cube, so neighboring blocks must not
                // cull their faces against it.
                .noOcclusion()
                // A piston would push one quarter of a table and leave the other three, which
                // is a table broken in a way nothing else in the mod can produce.
                .pushReaction(PushReaction.BLOCK));
    }

    public static Item createTableItem() {
        return new TableBlockItem(TABLE.get(), new Item.Properties());
    }

    /**
     * A table cut from stone rather than built from planks.
     * <p>Everything that makes a table a table is in {@link TableBlock} and none of it is
     * here: this only says how heavy it is, what it sounds like, and what color it is on a
     * map. The rest - clustering, seats, the session, the dyeable felt - a stone table gets
     * by being a {@code TableBlock}, which is how every rule in the mod already asks.
     */
    private static Block createStoneTable(MapColor color, float strength, int light) {
        return new TableBlock(BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(strength)
                .sound(SoundType.STONE)
                .lightLevel(state -> light)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
    }

    /** Rough and heavy: the table in a tavern, a dungeon, or anywhere built out of rubble. */
    public static Block createCobblestoneTable() {
        return createStoneTable(MapColor.STONE, 2.5f, 0);
    }

    /** Cut and polished, with a chiseled course under the top. For somewhere formal. */
    public static Block createBlackstoneTable() {
        return createStoneTable(MapColor.COLOR_BLACK, 3.0f, 0);
    }

    /**
     * One block of stone on a single plinth, and it glows.
     * <p>Dimmer than the vanilla block it is cut from: crying obsidian lights a room at ten,
     * and four quarters of a table doing that would be a lamp somebody plays cards on. Seven
     * reads as a glow at the table without lighting the building it is in.
     */
    public static Block createCryingObsidianTable() {
        return createStoneTable(MapColor.COLOR_BLACK, 5.0f, 7);
    }

    public static Item createCobblestoneTableItem() {
        return new TableBlockItem(COBBLESTONE_TABLE.get(), new Item.Properties());
    }

    public static Item createBlackstoneTableItem() {
        return new TableBlockItem(BLACKSTONE_TABLE.get(), new Item.Properties());
    }

    public static Item createCryingObsidianTableItem() {
        return new TableBlockItem(CRYING_OBSIDIAN_TABLE.get(), new Item.Properties());
    }

    /**
     * The block entity itself. Building its {@code BlockEntityType} is each loader's job.
     * <p>{@code BlockEntityType.BlockEntitySupplier} is package-private in vanilla and only
     * public where a loader has access-transformed it, so the builder cannot be named here -
     * :common compiles against vanilla and nothing else. Both loaders have their own way to
     * build one; this keeps the part that is actually the mod on this side of the line and
     * leaves them two lines each.
     */
    public static TableBlockEntity createTableEntity(BlockPos pos, BlockState state) {
        return new TableBlockEntity(pos, state);
    }

    /**
     * Where a collection lives.
     * <p>Stone rather than wood, and harder than a table: this is the one block in the mod
     * with somebody's whole collection inside it, and a block that takes a moment to break is
     * a block nobody breaks by accident while clearing a wall.
     */
    public static Block createCollection() {
        return new dev.gathering.block.CollectionBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .strength(3.0f)
                .sound(SoundType.WOOD)
                // Pistons would take the block and leave the block entity, which is a
                // collection deleted by a redstone accident.
                .pushReaction(PushReaction.BLOCK));
    }

    public static Item createCollectionItem() {
        return new net.minecraft.world.item.BlockItem(
                COLLECTION.get(), new Item.Properties().stacksTo(1));
    }

    public static dev.gathering.block.CollectionBlockEntity createCollectionEntity(
            BlockPos pos, BlockState state) {
        return new dev.gathering.block.CollectionBlockEntity(pos, state);
    }
}
