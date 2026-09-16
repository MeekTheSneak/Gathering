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
    public static final String SCOREKEEPERS_DESK_ID = "scorekeepers_desk";
    public static final String CHAIR_ID = "chair";
    public static final String DISPLAY_CASE_ID = "display_case";
    public static final String COBBLESTONE_CHAIR_ID = "cobblestone_chair";
    public static final String BLACKSTONE_CHAIR_ID = "blackstone_chair";
    public static final String CRYING_OBSIDIAN_CHAIR_ID = "crying_obsidian_chair";


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

    /**
     * The other woods every wooden thing in the mod is also made of.
     * <p>Every wood there is. Each wooden thing keeps the plain id for the wood it was already drawn in - the
     * chair is oak, the rest are dark oak - so a world built before this keeps its furniture, and the other
     * ten are named for their wood, the way vanilla names a door or a sign.
     * <p>Bamboo's planks are a block of bamboo mosaic's neighbour rather than a tree's, and the two nether
     * stems are not wood at all botanically; all three are planks in the hand and in the recipe book, which is
     * what a player means by "in every wood".
     */
    public static final java.util.List<String> WOODS = java.util.List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
            "crimson", "warped");

    /**
     * What a wooden thing is: the same block, in one wood or another.
     * <p>Each carries the wood it was already drawn in, because they are not all the same one - the chair is
     * oak and the rest are dark oak - and that wood keeps the plain id. Getting this wrong registers a block
     * whose model was written for another name, which draws as the missing model and nothing else says so.
     * <p>{@code tools/woodwork.py} reads these lines to know which wood not to write.
     */
    public enum Woodwork {
        TABLE(TABLE_ID, "dark_oak"),
        CHAIR(CHAIR_ID, "oak"),
        SHOP_COUNTER(SHOP_COUNTER_ID, "dark_oak"),
        COLLECTION(COLLECTION_ID, "dark_oak"),
        SCOREKEEPERS_DESK(SCOREKEEPERS_DESK_ID, "dark_oak"),
        DISPLAY_CASE(DISPLAY_CASE_ID, "dark_oak");

        private final String plain;
        private final String plainWood;

        Woodwork(String plain, String plainWood) {
            this.plain = plain;
            this.plainWood = plainWood;
        }

        /** The wood this thing is already drawn in, which keeps the plain id. */
        public String plainWood() {
            return plainWood;
        }

        /** The id of this thing in this wood: {@code spruce_table}, and the plain {@code table} for its own wood. */
        public String idFor(String wood) {
            return wood == null || wood.isBlank() || plainWood.equals(wood) ? plain : wood + "_" + plain;
        }
    }

    /** One wooden block in one wood: what to register, and where its block and item are bound. */
    public record WoodVariant(Woodwork kind, String wood, String id,
            Registered<Block> block, Registered<Item> item) {

        public Block createBlock() {
            return switch (kind) {
                case TABLE -> createTable();
                case CHAIR -> createChair();
                case SHOP_COUNTER -> createShopCounter();
                case COLLECTION -> createCollection();
                case SCOREKEEPERS_DESK -> createScorekeepersDesk();
                case DISPLAY_CASE -> createDisplayCase();
            };
        }

        public Item createItem() {
            return switch (kind) {
                case TABLE -> new TableBlockItem(block.get(), new Item.Properties());
                case CHAIR -> new DescribedBlockItem(block.get(), new Item.Properties(),
                        java.util.List.of("tooltip.gathering.chair_sit", "tooltip.gathering.chair_stand"));
                case SHOP_COUNTER -> new DescribedBlockItem(block.get(), new Item.Properties(),
                        java.util.List.of("tooltip.gathering.shop_counter_job"));
                case COLLECTION -> new DescribedBlockItem(block.get(), new Item.Properties().stacksTo(1),
                        java.util.List.of("tooltip.gathering.collection_open", "tooltip.gathering.collection_put_in",
                                "tooltip.gathering.collection_sweep"));
                case SCOREKEEPERS_DESK -> new DescribedBlockItem(block.get(), new Item.Properties(),
                        java.util.List.of("tooltip.gathering.desk_host", "tooltip.gathering.desk_anybody"),
                        "tooltip.gathering.desk_board");
                case DISPLAY_CASE -> new DescribedBlockItem(block.get(), new Item.Properties(),
                        java.util.List.of("tooltip.gathering.display_case_show",
                                "tooltip.gathering.display_case_take"));
            };
        }
    }

    private static final java.util.List<WoodVariant> WOOD_VARIANTS = everyWood();

    private static java.util.List<WoodVariant> everyWood() {
        java.util.List<WoodVariant> made = new java.util.ArrayList<>();
        for (Woodwork kind : Woodwork.values()) {
            for (String wood : WOODS) {
                if (wood.equals(kind.plainWood())) {
                    continue;
                }
                String id = kind.idFor(wood);
                made.add(new WoodVariant(kind, wood, id, new Registered<>(id), new Registered<>(id)));
            }
        }
        return java.util.List.copyOf(made);
    }

    /**
     * Every wooden block in every wood but dark oak, which the plain ids already are. Walked by both loaders
     * to register them, and by everything that has to name all of one kind - the block entities' valid blocks,
     * the creative tab, Create's display sources.
     */
    public static java.util.List<WoodVariant> woodVariants() {
        return WOOD_VARIANTS;
    }

    /** Every wooden block of one kind in the other woods. */
    public static java.util.List<WoodVariant> woodVariants(Woodwork kind) {
        return WOOD_VARIANTS.stream().filter(variant -> variant.kind() == kind).toList();
    }

    /** Every block of this kind, the plain dark oak one first. */
    public static java.util.List<Registered<Block>> allOf(Woodwork kind) {
        java.util.List<Registered<Block>> all = new java.util.ArrayList<>();
        all.add(switch (kind) {
            case TABLE -> TABLE;
            case CHAIR -> CHAIR;
            case SHOP_COUNTER -> SHOP_COUNTER;
            case COLLECTION -> COLLECTION;
            case SCOREKEEPERS_DESK -> SCOREKEEPERS_DESK;
            case DISPLAY_CASE -> DISPLAY_CASE;
        });
        woodVariants(kind).forEach(variant -> all.add(variant.block()));
        return java.util.List.copyOf(all);
    }

    /**
     * Every block whose fabric takes dye, in every wood.
     * <p>Listed once, because both loaders register the same color handler and the list had been written
     * out by hand on each - which is how the ten wooden shop counters, desks and cabinets ended up with a
     * felt property nothing tinted: dyeing a spruce cabinet changed its state and not its color.
     */
    public static java.util.List<Registered<Block>> everyDyedBlock() {
        java.util.List<Registered<Block>> all = new java.util.ArrayList<>(java.util.List.of(
                SHOP_COUNTER, SCOREKEEPERS_DESK, COLLECTION, CHAIR, DISPLAY_CASE,
                COBBLESTONE_CHAIR, BLACKSTONE_CHAIR, CRYING_OBSIDIAN_CHAIR));
        for (Woodwork kind : java.util.List.of(Woodwork.SHOP_COUNTER, Woodwork.SCOREKEEPERS_DESK,
                Woodwork.COLLECTION, Woodwork.CHAIR, Woodwork.DISPLAY_CASE)) {
            woodVariants(kind).forEach(variant -> all.add(variant.block()));
        }
        return java.util.List.copyOf(all);
    }

    /** Every table, in the order they are offered: the four materials, then the same table in every wood. */
    public static java.util.List<Registered<Block>> tables() {
        java.util.List<Registered<Block>> all = new java.util.ArrayList<>(java.util.List.of(
                TABLE, COBBLESTONE_TABLE, BLACKSTONE_TABLE, CRYING_OBSIDIAN_TABLE));
        woodVariants(Woodwork.TABLE).forEach(variant -> all.add(variant.block()));
        return java.util.List.copyOf(all);
    }

    /** Every table's item, in the same order. */
    public static java.util.List<Registered<Item>> tableItems() {
        java.util.List<Registered<Item>> all = new java.util.ArrayList<>(java.util.List.of(
                TABLE_ITEM, COBBLESTONE_TABLE_ITEM, BLACKSTONE_TABLE_ITEM, CRYING_OBSIDIAN_TABLE_ITEM));
        woodVariants(Woodwork.TABLE).forEach(variant -> all.add(variant.item()));
        return java.util.List.copyOf(all);
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
    public static final Registered<Block> DISPLAY_CASE = new Registered<>(DISPLAY_CASE_ID);
    public static final Registered<Item> DISPLAY_CASE_ITEM = new Registered<>(DISPLAY_CASE_ID);
    public static final Registered<BlockEntityType<dev.gathering.block.DisplayCaseBlockEntity>>
            DISPLAY_CASE_ENTITY = new Registered<>(dev.gathering.block.DisplayCaseBlockEntity.ID);
    public static final Registered<Block> CHAIR = new Registered<>(CHAIR_ID);
    public static final Registered<Item> CHAIR_ITEM = new Registered<>(CHAIR_ID);
    public static final Registered<Block> COBBLESTONE_CHAIR = new Registered<>(COBBLESTONE_CHAIR_ID);
    public static final Registered<Item> COBBLESTONE_CHAIR_ITEM = new Registered<>(COBBLESTONE_CHAIR_ID);
    public static final Registered<Block> BLACKSTONE_CHAIR = new Registered<>(BLACKSTONE_CHAIR_ID);
    public static final Registered<Item> BLACKSTONE_CHAIR_ITEM = new Registered<>(BLACKSTONE_CHAIR_ID);
    public static final Registered<Block> CRYING_OBSIDIAN_CHAIR = new Registered<>(CRYING_OBSIDIAN_CHAIR_ID);
    public static final Registered<Item> CRYING_OBSIDIAN_CHAIR_ITEM = new Registered<>(CRYING_OBSIDIAN_CHAIR_ID);

    public static final Registered<net.minecraft.world.entity.EntityType<dev.gathering.block.ChairSeat>> CHAIR_SEAT =
            new Registered<>(dev.gathering.block.ChairSeat.ID);
    public static final Registered<Block> SCOREKEEPERS_DESK = new Registered<>(SCOREKEEPERS_DESK_ID);
    public static final Registered<Item> SCOREKEEPERS_DESK_ITEM = new Registered<>(SCOREKEEPERS_DESK_ID);
    public static final Registered<BlockEntityType<dev.gathering.block.ScorekeepersDeskBlockEntity>>
            SCOREKEEPERS_DESK_ENTITY = new Registered<>(dev.gathering.block.ScorekeepersDeskBlockEntity.ID);

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
                .sound(SoundType.WOOD)
                // Its cabinet stands a pixel back from the front of the block and stops short of the top, so
                // it is not the cube it claimed to be: the block under one lost its top face and you could
                // see through the floor along the front of the counter.
                .noOcclusion());
    }

    public static Item createShopCounterItem() {
        return new DescribedBlockItem(SHOP_COUNTER.get(), new Item.Properties(),
                java.util.List.of("tooltip.gathering.shop_counter_job"));
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
                // Its drawers stand out in front of it and its back is a pixel short of the block, so it is not
                // the full cube it was drawn as: without this the game hid the faces of whatever was beside it,
                // and you could see through the world past the drawers.
                .noOcclusion()
                // Pistons would take the block and leave the block entity, which is a
                // collection deleted by a redstone accident.
                .pushReaction(PushReaction.BLOCK));
    }

    /**
     * One card under glass. Wood, glass and a wool mat, all of them vanilla: the case is a frame around
     * somebody else's card and has no art of its own to need.
     */
    public static Block createDisplayCase() {
        return new dev.gathering.block.DisplayCaseBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                // Glass and air, which is nothing like a cube.
                .noOcclusion());
    }

    public static Item createDisplayCaseItem() {
        return new DescribedBlockItem(DISPLAY_CASE.get(), new Item.Properties(),
                java.util.List.of("tooltip.gathering.display_case_show", "tooltip.gathering.display_case_take"));
    }

    public static Item createCollectionItem() {
        return new DescribedBlockItem(COLLECTION.get(), new Item.Properties().stacksTo(1), java.util.List.of(
                "tooltip.gathering.collection_open", "tooltip.gathering.collection_put_in",
                "tooltip.gathering.collection_sweep"));
    }

    public static dev.gathering.block.CollectionBlockEntity createCollectionEntity(
            BlockPos pos, BlockState state) {
        return new dev.gathering.block.CollectionBlockEntity(pos, state);
    }

    /** Wood, like the lectern it borrows its look from, and as easy to move. */
    public static Block createScorekeepersDesk() {
        return new dev.gathering.block.ScorekeepersDeskBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .sound(SoundType.WOOD)
                // A foot, a pedestal and a shelf, which is nothing like a cube.
                .noOcclusion());
    }

    /** Wood, and as light to move as any chair. */
    public static Block createChair() {
        return new dev.gathering.block.ChairBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                .noOcclusion());
    }

    public static Item createChairItem() {
        return new DescribedBlockItem(CHAIR.get(), new Item.Properties(),
                java.util.List.of("tooltip.gathering.chair_sit", "tooltip.gathering.chair_stand"));
    }

    /** Stone seating uses the same seat entity and interactions as the original wooden chair. */
    public static Block createStoneChair(net.minecraft.world.level.material.MapColor color, float hardness,
            net.minecraft.world.level.block.SoundType sound) {
        return new dev.gathering.block.MaterialChairBlock(BlockBehaviour.Properties.of()
                .mapColor(color).strength(hardness).sound(sound).requiresCorrectToolForDrops().noOcclusion());
    }

    public static Item createStoneChairItem(Block block) {
        return new DescribedBlockItem(block, new Item.Properties(),
                java.util.List.of("tooltip.gathering.chair_sit", "tooltip.gathering.chair_stand", "tooltip.gathering.chair_dye"));
    }

    /** The invisible thing a player sitting in a chair rides: tiny, unsaved, and never sent to be drawn as anything. */
    public static net.minecraft.world.entity.EntityType<dev.gathering.block.ChairSeat> createChairSeat() {
        return net.minecraft.world.entity.EntityType.Builder
                .<dev.gathering.block.ChairSeat>of(dev.gathering.block.ChairSeat::new, net.minecraft.world.entity.MobCategory.MISC)
                .sized(0.001f, 0.001f)
                .noSave()
                .noSummon()
                .clientTrackingRange(10)
                .build(dev.gathering.Gathering.MOD_ID + ":" + dev.gathering.block.ChairSeat.ID);
    }

    public static Item createScorekeepersDeskItem() {
        return new DescribedBlockItem(SCOREKEEPERS_DESK.get(), new Item.Properties(),
                java.util.List.of("tooltip.gathering.desk_host", "tooltip.gathering.desk_anybody"), "tooltip.gathering.desk_board");
    }

    public static dev.gathering.block.ScorekeepersDeskBlockEntity createScorekeepersDeskEntity(
            BlockPos pos, BlockState state) {
        return new dev.gathering.block.ScorekeepersDeskBlockEntity(pos, state);
    }
}
