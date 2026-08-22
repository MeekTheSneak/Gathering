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
    public static final String TABLE_ID = "table";

    public static final Registered<Item> CARD = new Registered<>(CARD_ID);
    public static final Registered<Item> DECK = new Registered<>(DECK_ID);
    public static final Registered<Block> TABLE = new Registered<>(TABLE_ID);
    public static final Registered<Item> TABLE_ITEM = new Registered<>(TABLE_ID);
    public static final Registered<BlockEntityType<TableBlockEntity>> TABLE_ENTITY =
            new Registered<>(TableBlockEntity.ID);

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

    public static Block createTable() {
        return new TableBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                // Not flammable: a table with a live session on it burning down is a way to
                // lose a game that nobody signed up for.
                .ignitedByLava());
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
}
