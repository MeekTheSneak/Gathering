package dev.gathering.client;

import dev.gathering.block.TableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What color a table's felt is drawn.
 *
 * <p>The felt is one texture tinted per table rather than sixteen textures or sixteen
 * blockstates: color is a thing that is only ever asked about while drawing, and putting it
 * in the blockstate would multiply every table's state count by sixteen to answer a question
 * nothing else asks.
 *
 * <p>Client-only, and shared by both loaders - only the way a color handler is registered
 * differs between them.
 */
public final class TableColors {

    /** No tint: the felt texture's own color, which is the undyed table. */
    public static final int UNDYED = 0xFFFFFF;

    /** The felt faces carry this tint index; everything else on the table is wood. */
    public static final int FELT_TINT_INDEX = 0;

    private TableColors() {
    }

    public static int tintOf(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (tintIndex != FELT_TINT_INDEX || level == null || pos == null) {
            return UNDYED;
        }
        return TableBlock.entityAt(level, pos)
                .flatMap(table -> table.felt())
                .map(TableColors::rgbOf)
                .orElse(UNDYED);
    }

    /** The table in an inventory is always undyed: the item carries no color. */
    public static int itemTintOf(int tintIndex) {
        return UNDYED;
    }

    private static int rgbOf(DyeColor color) {
        int packed = color.getTextureDiffuseColor();
        // getTextureDiffuseColor is ARGB and a block tint is RGB; leaving the alpha in makes
        // every dyed table draw as though it were unlit.
        return packed & 0x00FFFFFF;
    }
}
