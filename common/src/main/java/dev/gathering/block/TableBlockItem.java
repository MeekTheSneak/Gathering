package dev.gathering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a whole table, or none of it.
 *
 * <p>The block clicked becomes the table's north-west corner and the table extends east and
 * south from it. All four quarters go down in one go: a table that placed its first quarter
 * and then discovered there was no room for the fourth would leave a stump behind, and the
 * player would have to work out which of the blocks in front of them was the wrong one.
 */
public class TableBlockItem extends BlockItem {

    public TableBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        BlockPos origin = context.getClickedPos();
        if (!TableBlock.canPlaceAt(context, origin)) {
            return false;
        }

        Level level = context.getLevel();
        for (TablePart part : TablePart.values()) {
            level.setBlock(
                    part.offsetFrom(origin),
                    state.setValue(TableBlock.PART, part),
                    Block.UPDATE_ALL);
        }
        return true;
    }
}
