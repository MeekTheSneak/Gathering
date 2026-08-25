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
 *
 * <p>The factories live here rather than in the loader modules so both loaders register
 * identical objects; only the registration mechanism differs between them, which is exactly
 * the size the platform-specific surface is supposed to be.
 */
public final class GatheringContent {

    public static final String CARD_ID = "card";
    public static final String DECK_ID = "deck";
    public static final String PACK_ID = "pack";
    public static final String TABLE_ID = "table";
    public static final String COLLECTION_ID = "collection";
    public static final String SEALED_ID = "sealed";
    public static final String SHOP_COUNTER_ID = "shop_counter";

    public static final Registered<Item> CARD = new Registered<>(CARD_ID);
    public static final Registered<Item> DECK = new Registered<>(DECK_ID);
    public static final Registered<Item> PACK = new Registered<>(PACK_ID);
    public static final Registered<Block> TABLE = new Registered<>(TABLE_ID);
    public static final Registered<Item> TABLE_ITEM = new Registered<>(TABLE_ID);
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
     *
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
     *
     * <p>Not as far, though. A box is a thing you buy one or two of and open; a chest with
     * sixteen cases in it is not a collection, it is a warehouse.
     */
    public static Item createSealed() {
        return new SealedItem(new Item.Properties().stacksTo(8));
    }

    /**
     * The counter a shopkeeper works behind.
     *
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
                // The model is a table rather than a cube, so neighbouring blocks must not
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
     * The block entity itself. Building its {@code BlockEntityType} is each loader's job.
     *
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
     *
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
